package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static gitlet.Utils.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/** Represents remote repositories and used in gitlet commands involving remote e.g. add-remote,
 * rm-remote, push, fetch and pull.
 */
public class RemoteRepo implements Serializable {
    /** Map from name of remote to directory (string) of remote. */
    static Map<String, RemoteRepo> remoteNameToRemoteRepoMap = new StringRemoteRepoHashMap();
    /** File storing information about last saved remoteNameToDirMap */
    static File remoteNameToRemoteRepoFile = join(Repository.GITLET_DIR, "remoteMap");
    /** Name of remote. */
    String remoteName;
    /** Path (string) of remote directory. */
    String remoteDirPath;
    /** Location on disk where remote directory is stored. */
    File remoteDir;
    /** Working directory of the remote directory. */
    File remoteGitletDir;
    /** Staging directory of the remote directory. */
    File remoteStage;
    /** Folder storing commits in the remote directory. */
    File remoteCommits;
    /** Directory where the contents (blobs) of commits are stored in remote directory. */
    File remoteBlobs;
    /** HeadMap, which stores branches and their head commits, of the remote directory. */
    transient Map<String, String> remoteHeadMap;
    /** Location on disk where remoteHeadMap is stored. */
    File remoteHeadMapFile;
    /** List of commits to be copied over to remote branch. See push command. */
    Set<String> commitTargets = new HashSet<>();

    RemoteRepo(String remoteName, String remoteDirPath) {
        remoteDirPath = remoteDirPath.replace("/", File.separator);
        remoteDirPath = remoteDirPath.replace("\\", File.separator);
        remoteDirPath = remoteDirPath.replace(".gitlet", "");
        this.remoteDirPath = remoteDirPath.replace(File.separator + File.separator,
                File.separator);
        this.remoteName = remoteName;
        this.remoteDir = join(Repository.CWD, this.remoteDirPath);
        this.remoteGitletDir = join(this.remoteDirPath, ".gitlet");
        this.remoteStage = join(remoteGitletDir, "stage");
        this.remoteCommits = join(remoteGitletDir, "commits");
        this.remoteBlobs = join(remoteGitletDir, "blobs");
        this.remoteHeadMapFile = join(remoteGitletDir, "headMap");
    }

    /** Associate given remote name with given remote directory such that pushing, fetching and
     * pulling can be called by the given remote name. User name and server information are not
     * checked in this method. Print error if remote name exists.
     */
    static void addRemote(String remoteName, String remoteDirPath){
        importRemoteNameToRemoteRepoMap();
        if (remoteNameToRemoteRepoMap.containsKey(remoteName)) {
            System.out.println("A remote with that name already exists.");
            System.exit(0);
        }
        RemoteRepo remote = new RemoteRepo(remoteName, remoteDirPath);
        remoteNameToRemoteRepoMap.put(remoteName, remote);
        writeRemoteNameToRemoteRepoMap();
    }

    /** Remote the association of given remote name with given remote directory. Print error if
     * the given remote name does not exist. */
    static void rmRemote(String remoteName){
        importRemoteNameToRemoteRepoMap();
        if (!remoteNameToRemoteRepoMap.containsKey(remoteName)) {
            System.out.println("A remote with that name does not exist.");
            System.exit(0);
        }
        remoteNameToRemoteRepoMap.remove(remoteName);
        writeRemoteNameToRemoteRepoMap();
    }

    /** Push from local current branch to given remoteBranchName in remoteName */
    static void push(String remoteName, String remoteBranchName){
        Repository.checkUncommittedChanges();
        importRemoteNameToRemoteRepoMap();
        RemoteRepo remote = checkForRemoteGitletDir(remoteName);
        importRemoteHeadMap(remote);
        checkForRemoteBranch(remote, remoteBranchName);
        Repository.importHeadMap();

        remote.pushHelper(remoteBranchName);

        writeRemoteHeadMap(remote);
    }

    /** Fetch from remoteBranchName in remoteName to a local mirror branch named
     * "remoteName/remoteBranchName". */
    static void fetch(String remoteName, String remoteBranchName){
        importRemoteNameToRemoteRepoMap();
        RemoteRepo remote = checkForRemoteGitletDir(remoteName);
        importRemoteHeadMap(remote);
        checkForRemoteBranch(remote, remoteBranchName);
        Repository.importHeadMap();

        remote.fetchHelper(remoteBranchName);

        writeObject(join(Repository.GITLET_DIR, "headMap"),
                (Serializable) Repository.headMap);
    }

    /** Fetch branch named "remoteName/remoteBranchName". Merge that mirror branch to current
     * branch. */
    static void pull(String remoteName, String remoteBranchName){
        fetch(remoteName, remoteBranchName);
        String mirrorBranchName = remoteName + "/" + remoteBranchName;
        Repository.merge(mirrorBranchName);
    }

    /* ============================================================================================
       Command-specific helper functions
       ============================================================================================
     */

    /** Append the local current branch's commits and blobs to given branch at given
     * remote directory (only the commits not already in given branch). Create a new branch if
     * necessary. Then, reset the remote branch to local currend head (fast-forwarding). Print
     * error if the remote branch's head is not an ancestor of local current head, or if the remote
     * branch is already up-to-date with local current branch. */
    private void pushHelper(String remoteBranchName){
        String localHeadHash = Repository.getHeadHash();
        if (remoteHeadMap.containsKey(remoteBranchName)) {
            String remoteBranchHeadHash = remoteHeadMap.get(remoteBranchName);
            if (localHeadHash.equals(remoteBranchHeadHash)) {
                System.out.println("Remote is already up-to-date. No need to push.");
                System.exit(0);
            }
            // get a list of commits on local branch that are in the future of remote branch head
            scanOverBranch(localHeadHash, remoteBranchHeadHash, Commit.COMMITS);
            // check if remoteBranchHead is an ancestor of local head
            if (!commitTargets.remove(remoteBranchHeadHash)) {
                System.out.println("Please pull down remote changes before pushing.");
                System.exit(0);
            }
        }
        copyCommitsBlobsAcrossBranches(Repository.GITLET_DIR, remoteGitletDir);
        remoteReset(remoteBranchName, localHeadHash);
    }

    /** Copy all commits from the given branch in remote directory into a local branch named
     * "remoteName/remoteBranchName" (if it does not already exist). Also copy across all relevant
     * blobs. Point the head of the new branch to the remote branch head. Create a new branch
     * if necessary. */
    private void fetchHelper(String remoteBranchName){
        String mirrorBranchName = this.remoteName + "/" + remoteBranchName;
        String mirrorBranchHeadHash = Repository.headMap.get(mirrorBranchName);
        if (mirrorBranchHeadHash != null){
            String remoteBranchHeadHash = remoteHeadMap.get(remoteBranchName);
            scanOverBranch(remoteBranchHeadHash, plainFilenamesIn(Commit.COMMITS), remoteCommits);
        }
        copyCommitsBlobsAcrossBranches(remoteGitletDir, Repository.GITLET_DIR);
        // adjust head pointer
        Repository.headMap.put(mirrorBranchName, remoteHeadMap.get(remoteBranchName));
    }

    /** Using recursion, keep tracing back to parent commit hashes, starting from given hash to
     * initial commit. Terminate the recursion if hash is equal to referenceBranchHash. Return a
     * list of commit hashes traversed, which represents a list of commits on current branch that
     * are beyond the last common ancestor commit of current branch and reference branch. */
    private void scanOverBranch(String hash, String referenceCommitHash, File commitsFolder) {
        if (hash == null) {
            return;
        }
        commitTargets.add(hash);
        if (hash.equals(referenceCommitHash)) {
            return;
        }
        Commit commit = readObject(join(commitsFolder, hash), Commit.class);
        scanOverBranch(commit.getParentCommitHash(), referenceCommitHash, commitsFolder);
        scanOverBranch(commit.getSecondParentCommitHash(), referenceCommitHash, commitsFolder);
    }

    /** Using recursion, keep tracing back to parent commit hashes, starting from given hash to
     * initial commit. Terminate the recursion if hash is in the given listOfCommitHashes. Return a
     * list of commit hashes traversed, which represents a list of commits on current branch that
     * are beyond the last common ancestor commit of current branch and reference branch. */
    private void scanOverBranch(String hash, List<String> referenceCommitHashes
                                , File commitsFolder) {
        if (hash == null) {
            return;
        }
        commitTargets.add(hash);
        if (referenceCommitHashes.contains(hash)) {
            return;
        }
        Commit commit = readObject(join(commitsFolder, hash), Commit.class);
        scanOverBranch(commit.getParentCommitHash(), referenceCommitHashes, commitsFolder);
        scanOverBranch(commit.getSecondParentCommitHash(), referenceCommitHashes, commitsFolder);
    }

    /** Copy commit and blob files from folders in srcFolder to folders in dstFolder. If
     * commitTargets is provided, only copy commits included in commitTargets. */
    private void copyCommitsBlobsAcrossBranches(File srcFolder, File dstFolder){
        List<String> listOfCommits = (commitTargets.isEmpty())
                ? plainFilenamesIn(join(srcFolder, "commits"))
                : new ArrayList<>(commitTargets);
        // copy commits from source folder to destination folder
        for (String c : listOfCommits) {
            Path src = Paths.get(srcFolder.toString(), "commits", c);
            Path dst = Paths.get(dstFolder.toString(), "commits", c);
            if (!Files.exists(dst)) {
                try {
                    Files.copy(src, dst);
                } catch (IOException e) {
                    throw Utils.error("IOException during file copy operation.");
                }
            }
        }
        // copy blobs from source folder to destination folder
        for (String blob : plainFilenamesIn(join(srcFolder, "blobs"))) {
            Path src = Paths.get(srcFolder.toString(), "blobs", blob);
            Path dst = Paths.get(dstFolder.toString(), "blobs", blob);
            if (!Files.exists(dst)) {
                try {
                    Files.copy(src, dst);
                } catch (IOException e) {
                    throw Utils.error("IOException during file copy operation.");
                }
            }
        }
    }

    /** Copy files tracked by remote branch head to working dir of remote repo. Adjust the head
     * pointer of remote branch to point to headHash. */
    private void remoteReset(String remoteBranchName, String headHash){
        Map<String, String> headBlobMap =
                Repository.getCommitFromHash(headHash).getBlobMap();
        for (Map.Entry<String, String> blobPair : headBlobMap.entrySet()) {
            Path src = Paths.get(remoteBlobs.toString(), blobPair.getValue());
            Path dst = Paths.get(remoteDirPath, blobPair.getKey());
            try {
                Files.copy(src, dst, REPLACE_EXISTING);
            } catch (IOException e) {
                throw Utils.error("IOException during file copy operation.");
            }
        }
        remoteHeadMap.put(remoteBranchName, headHash);
    }

    /* ============================================================================================
       General helper functions
       ============================================================================================
     */

    /** Check if given remote name corresponds to a remote directory and if the directory
     * contains a .gitlet directory. */
    static RemoteRepo checkForRemoteGitletDir(String remoteName) {
        RemoteRepo remote = remoteNameToRemoteRepoMap.get(remoteName);
        if (remote == null || !remote.remoteGitletDir.exists()) {
            System.out.println("Remote directory not found.");
            System.exit(0);
        }
        return remote;
    }

    /** Check if the given branch exists in the given remote repository. */
    static void checkForRemoteBranch(RemoteRepo remote, String remoteBranchName){
        if (remote.remoteHeadMap.get(remoteBranchName) == null){
            System.out.println("That remote does not have that branch.");
            System.exit(0);
        }
    }

    /** Import mapping from remote name to remote directory from disk. */
    static void importRemoteNameToRemoteRepoMap(){
        if (remoteNameToRemoteRepoMap.isEmpty() && remoteNameToRemoteRepoFile.exists()) {
            remoteNameToRemoteRepoMap = readObject(remoteNameToRemoteRepoFile,
                    StringRemoteRepoHashMap.class);
        }
    }

    /** Import mapping from remote branch name to head hash from disk. */
    static void importRemoteHeadMap(RemoteRepo remote){
        remote.remoteHeadMap = readObject(remote.remoteHeadMapFile, Repository.StringTreeMap.class);
    }

    /** Update remoteNameToRemoteRepoFile on disk. */
    static void writeRemoteNameToRemoteRepoMap(){
        writeObject(remoteNameToRemoteRepoFile, (Serializable) remoteNameToRemoteRepoMap);
    }

    /** Update remoteHeadMapFile on disk. */
    static void writeRemoteHeadMap(RemoteRepo remote){
        writeObject(remote.remoteHeadMapFile, (Serializable) remote.remoteHeadMap);
    }

    static class StringRemoteRepoHashMap extends HashMap<String, RemoteRepo>{}
}