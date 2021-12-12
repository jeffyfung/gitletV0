package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.nio.file.StandardCopyOption.*;
import static gitlet.Utils.*;

/** Represents a gitlet repository, which is a simplified version of git.
 *  @author Jeffrey Fung
 */
public class Repository {

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /** The staging area directory */
    static final File STAGE = join(GITLET_DIR, "stage");
    /** The blobs directory */
    static final File BLOBS = join(GITLET_DIR, "blobs");
    /** Map from branch name to commit head hash */
    static Map<String, String> headMap = new StringTreeMap();
    /** Name of current branch */
    static String currentBranch;
    /** label for files that are staged to be removed */
    static String keyString = "[[del[[";
    /** Length of keyString label */
    static int keyStringLen = keyString.length();

    /** Call setupPersistence to create folders holding .gitlet, stage, commits, blobs.
     * Make an initial commit by calling makeInitCommit. Print error if .gitlet already exists. */
    static void initRepo() {
        setupPersistence();
        Commit.makeInitCommit();
    }

    private static void setupPersistence() {
        if (!GITLET_DIR.mkdir()) {
            System.out.println("A Gitlet version-control system already exists in the current"
                    + "directory.");
            System.exit(0);
        }
        STAGE.mkdir();
        BLOBS.mkdir();
        Commit.COMMITS.mkdir();
    }

    /** Exit the program if gitlet directory has not been initialized in CWD */
    static void checkForGitletDir() {
        if (!GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
    }

    /** Copy a file from CWD to staging area. Overwrite previous entry of the same name
     *  in the staging area. Remove file from staging area if the file version being added
     *  is tracked by current commit, then exit. No action if the blob hash already
     *  exists in the staging area (i.e. content already exists).
     */
    static void add(String fileName) {
        if (!plainFilenamesIn(CWD).contains(fileName)) {
            System.out.println("File does not exist.");
            return;
        }
        String fileHash = sha1(readContents(join(CWD, fileName)));
        String curHeadBlobHash = getCommitFromHash(getHeadHash()).getBlobMap().get(fileName);
        if (curHeadBlobHash != null && curHeadBlobHash.equals(fileHash)) {
            join(STAGE, fileName).delete();
            join(STAGE, keyString.concat(fileName)).delete();
            // current commit contains the same version of file as the one in CWD
            // delete the file from CWD without any staging
            // return true if successfully deleted; return false if the file is not in STAGE
            // no further action required for both cases
        } else {
            try {
                Files.copy(join(CWD, fileName).toPath(), join(STAGE, fileName).toPath(),
                        REPLACE_EXISTING);
            } catch (IOException e) {
                throw Utils.error("IOException during file copy operation.");
            }
        }
    }

    /** Create a new commit whose parent commit is the HEAD of current branch.
     *  The new commit's blobMap is identical to its parent except for files in STAGE.
     *  Clear all files in STAGE afterwards */
    static void commit(String commitMsg) {
        Commit.makeCommit(commitMsg);
        clearStage();
    }

    /** 1) If the file is currently staged for addition, unstage it.
     *  2) If the file is tracked in the current commit, stage it for removal and
     *  if the file is in CWD, remove it from CWD*/
    static void remove(String fileName) {
        if (plainFilenamesIn(STAGE).contains(fileName)) {
            if (!join(STAGE, fileName).delete()) {
                throw Utils.error("Error when deleting %s from staging area.", fileName);
            }
        // check if current commit contains fileName
        } else if (getCommitFromHash(getHeadHash()).getBlobMap().containsKey(fileName)) {
            File fileStagedForRemoval = join(STAGE, keyString.concat(fileName));
            createFile(fileStagedForRemoval);
            // delete fileName from CWD. return false if fileName does not exist in CWD
            restrictedDelete(fileName);
        } else {
            System.out.println("No reason to remove the file.");
        }
    }

    /** Display the history of commit starting from current head commit to initial commit,
     *  starting with current commit. For merge commits, ignore second parents.
     *  Print out the commit id, time and commit message of each commit. */
    static void log() {
        logHelper(getHeadHash());
    }

    /** Display information of every commit in the gitlet repository in lexicographic
     *  order of their hash strings, formatted as specified in displayCommitInfo. */
    static void globalLog() {
        for (String curCommitHash : plainFilenamesIn(Commit.COMMITS)) {
            Commit curCommit = getCommitFromHash(curCommitHash);
            displayCommitInfo(curCommitHash, curCommit);
        }
    }

    /** Display the hashes of commits that have the input commit message, one per line,
     *  in lexicographical order of the hashes. */
    static void find(String msg) {
        // for each loop over a list of all commits hashes in COMMIT
        // for each hash, restore the commit object and its commitMsg -> display if equals
        boolean msgOutputted = false;
        for (String curCommitHash : plainFilenamesIn(Commit.COMMITS)) {
            if (getCommitFromHash(curCommitHash).getCommitMsg().equals(msg)) {
                System.out.println(curCommitHash);
                msgOutputted = true;
            }
        }
        if (!msgOutputted)  {
            System.out.println("Found no commit with that message.");
        }
    }

    /** Display information on branches, file(s) staged for addition or removal,
     *  modifications not staged for commit and untracked files. Mark the current
     *  branch with an asterisk. Entries listed in lexicographical order, not
     *  counting asterisk. */
    static void status() {
        List<String> filesInCWD = plainFilenamesIn(CWD);
        List<String> filesInStage = plainFilenamesIn(STAGE);
        Map<String, String> curCommitBlobMap = getCommitFromHash(getHeadHash()).getBlobMap();
        displayBranchInfo();
        displayStagedFiles(filesInStage);
        displayModificationsNotStagedForCommit(filesInCWD, filesInStage, curCommitBlobMap);
        displayUntrackedFiles(filesInCWD, filesInStage, curCommitBlobMap);
    }

    /** Copy the file as it exists in the head commit to CWD. Overwrite if necessary.
     *  No need to stage the file. */
    static void checkout(String fileName) {
        String blobHash = getCommitFromHash(getHeadHash()).getBlobMap().get(fileName);
        if (blobHash == null) {
            System.out.println("File does not exist in that commit.");
            return;
        }
        String fileContent = readContentsAsString(join(BLOBS, blobHash));
        writeContents(new File(fileName), fileContent);
    }

    /** Copy the file as it exists in the commit specified by the hash to CWD. Overwrite
     *  if necessary. No need to stage the file. */
    static void checkout(String commitHash, String fileName) {
        String blobHash = getCommitFromHash(commitHash).getBlobMap().get(fileName);
        if (blobHash == null) {
            System.out.println("File does not exist in that commit.");
            return;
        }
        String fileContent = readContentsAsString(join(BLOBS, blobHash));
        writeContents(new File(fileName), fileContent);
    }

    /** Copy all files tracked by the head commit of the given branch to CWD. Overwrite
     *  if necessary. Any file not tracked by the head commit of given branch
     *  will be deleted from CWD. Clear the staging area unless the given branch
     *  is the current branch. Set this as the current branch. */
    static void checkoutBranch(String branchName) {
        String targetHeadHash = getHeadHash(branchName);
        Map<String, String> curHeadBlobMap = getCommitFromHash(getHeadHash()).getBlobMap();
        for (String f : plainFilenamesIn(CWD)) {
            if (!curHeadBlobMap.containsKey(f)) {
                System.out.println("There is an untracked file in the way; delete it, "
                        + "or add and commit it first.");
                System.exit(0);
            }
        }
        // Delete all files in CWD
        for (String f : plainFilenamesIn(CWD)) {
            restrictedDelete(f);
        }
        // Copy files from branch head to CWD
        for (Map.Entry<String, String> blobPair
                : getCommitFromHash(targetHeadHash).getBlobMap().entrySet()) {
            writeContents(new File(blobPair.getKey()),
                    readContentsAsString(join(BLOBS, blobPair.getValue())));
        }
        // Clear stage if target branch is not current branch
        if (branchName.equals(currentBranch)) {
            System.out.println("No need to checkout the current branch");
        } else {
            clearStage();
        }
        // Set target branch to current branch
        currentBranch = branchName;
        writeContents(join(GITLET_DIR, "currentBranch"), currentBranch);
    }

    /** Create a new branch and point its head to current commit (via commit hash).
     *  Do not switch to the new branch. */
    static void branch(String branchName) {
        importHeadMap();
        if (headMap.containsKey(branchName)) {
            System.out.println("A branch with that name already exists.");
            return;
        }
        String headHash = getHeadHash();
        headMap.put(branchName, headHash);
        writeObject(join(GITLET_DIR, "headMap"), (Serializable) headMap);
    }

    /** Delete the branch with the given name. Retain all commits and files associated. */
    static void rmBranch(String branchName) {
        importCurrentBranch();
        if (currentBranch.equals(branchName)) {
            System.out.println("Cannot remove the current branch.");
            return;
        }
        importHeadMap();
        if (headMap.remove(branchName) == null) {
            System.out.println("A branch with that name does not exist.");
        } else {
            writeObject(join(GITLET_DIR, "headMap"), (Serializable) headMap);
        }
    }

    /** Check out all files tracked by the given commit. Removes tracked files that are not
     *  present in that commit. Move current branch's head to that commit. Clear the stage.
     *  Accept abbreviated commit id (i.e. first 8 characters of full id).
     */
    static void reset(String commitHash) {
        // warns if there is an untracked file in CWD
        Map<String, String> curHeadBlobMap = getCommitFromHash(getHeadHash()).getBlobMap();
        List<String> filesInStage = plainFilenamesIn(STAGE);
        for (String f : plainFilenamesIn(CWD)) {
            if (!curHeadBlobMap.containsKey(f) && !filesInStage.contains(f)) {
                System.out.println("There is an untracked file in the way; delete it, "
                        + "or add and commit it first.");
                System.exit(0);
            }
        }
        // delete all files in CWD
        for (String f : plainFilenamesIn(CWD)) {
            restrictedDelete(f);
        }
        // checkout all files tracked by given commit
        Map<String, String> targetBlobMap = getCommitFromHash(commitHash).getBlobMap();
        for (Map.Entry<String, String> blobPair : targetBlobMap.entrySet()) {
            Path src = Paths.get(BLOBS.toString(), blobPair.getValue());
            Path dest = Paths.get(CWD.toString(), blobPair.getKey());
            try {
                Files.copy(src, dest, REPLACE_EXISTING);
            } catch (IOException e) {
                throw Utils.error("IOException during file copy operation.");
            }
        }
        // reset current branch's head
        headMap.put(currentBranch, commitHash);
        writeObject(join(GITLET_DIR, "headMap"), (Serializable) headMap);
        // clear staging area
        clearStage();
    }

    /** Merge given branch into current branch.
     *
     *  If given branch's head and current branch's head are on the same line of commit:
     *      - if the given branch's head is an ancestor of that of current branch, fast
     *        forward to given branch's head.
     *      - if the current branch's head is an ancestor of that of given branch, do nothing.
     *      - if the current branch's head and given branch's head are the same, do nothing.
     *
     *  In cases where the branches are split from a split point, conduct the following action
     *  and then call commit (i.e. merge commit):
     *
     *      - if a file is changed in current branch but unmodified in given branch, do nothing.
     *      - if a file is changed in given branch but unmodified in current branch, add it to
     *        CWD and stage it.
     *      - if a file is deleted from current branch but unmodified in given branch, do nothing.
     *      - if a file is deleted from given branch but unmodified in current branch, remove it
     *        from CWD and stage it for removal.
     *      - if a file is modified / deleted / created in the same way in both branches, do
     *        nothing.
     *      - if a file is modified / deleted / created differently in different branches,
     *        including the case where a file is deleted in a branch and modified in the other,
     *        as well as the case where a file being newly created in both branches with
     *        different contents, merge conflict is encountered and file content will be
     *        updated as below:
     *
     *              <<<<<<< HEAD
     *              contents of file in current branch
     *              =======
     *              contents of file in given branch
     *              >>>>>>>
     *
     *        Then add the updated file to CWD and stage it.
     *      - if a file is newly created in current branch but not give branch, do nothing.
     *      - if a file is newly created in given branch but not current branch, add it to
     *        CWD and stage it.
     *
     *  A merge commit has two parent commits with first parent being the current branch's head
     *  and second parent being the given branch's head. Making commits with no staged files will
     *  display commit error. Merge does not take place when there are files untracked by current
     *  commit in CWD, or when there are staged (for addition or removal) files.
     *
     *  See notesOnMergeTest.md for further details */
    static void merge(String branchName) {
        String curHeadHash = getHeadHash();
        Map<String, String> curHeadBlobMap = getCommitFromHash(curHeadHash).getBlobMap();
        checkMergeGeneralFailureCases(branchName, curHeadBlobMap);

        String branchHeadHash = getHeadHash(branchName);
        Map<String, String> branchHeadBlobMap = getCommitFromHash(branchHeadHash).getBlobMap();

        // get split point and check if the two heads are equal, or if one is an ancestor
        // of another
        SplitPointFinder spFinder = new SplitPointFinder(curHeadHash, branchHeadHash, branchName);
        String splitPointHash = spFinder.getSplitPointOfBranches();
        Commit splitPoint = getCommitFromHash(splitPointHash);
        Map<String, String> splitPointBlobMap = splitPoint.getBlobMap();

        // get shallow copy of blobMaps
        Map<String, String> tmpCurHeadBlobMap = new HashMap<>(curHeadBlobMap);
        Map<String, String> tmpBranchHeadBlobMap = new HashMap<>(branchHeadBlobMap);
        int conflict = 0;
        for (Map.Entry<String, String> splitPointPair: splitPointBlobMap.entrySet()) {
            // compare and pop item from tmpCurHeadHeadBlobMap
            String fileName = splitPointPair.getKey();
            VersionComparator cur = compareFile(splitPointPair, fileName, tmpCurHeadBlobMap);
            VersionComparator branch = compareFile(splitPointPair, fileName, tmpBranchHeadBlobMap);
            if (cur.num == 1 && branch.num == 0) {
                // case 1a - no action
            } else if (cur.num == 0 && branch.num == 1) {
                // case 1b
                // change to version in the given branch (copy from BLOBS to CWD) and stage change
                try {
                    Files.copy(join(BLOBS, branch.blobHash).toPath(),
                            join(CWD, fileName).toPath(), REPLACE_EXISTING);
                    Files.copy(join(CWD, fileName).toPath(),
                            join(STAGE, fileName).toPath(), REPLACE_EXISTING);
                } catch (IOException e) {
                    throw Utils.error("IOException during file copy operation.");
                }
            } else if (cur.num == 2 && branch.num == 0) {
                // case 2a - no action
            } else if (cur.num == 0 && branch.num == 2) {
                // case 2b
                // remove file from CWD and stage for removal
                File fileStagedForRemoval = join(STAGE, keyString.concat(fileName));
                createFile(fileStagedForRemoval);
                restrictedDelete(fileName);
            } else if ((cur.num == 1 && branch.num == 1) || (cur.num == 1 && branch.num == 2)
                    || (cur.num == 2 && branch.num == 1)) {
                // case 3a - no action
                if (cur.blobHash == null || branch.blobHash == null
                        || !cur.blobHash.equals(branch.blobHash)) {
                    // case 3b - merge conflict
                    String curBlobContent = (cur.blobHash == null)
                            ? "" : readContentsAsString(join(BLOBS, cur.blobHash));
                    String branchBlobContent = (branch.blobHash == null)
                            ? "" : readContentsAsString(join(BLOBS, branch.blobHash));
                    conflict = handleMergeConflict(fileName, curBlobContent, branchBlobContent);
                }
            } else if (cur.num == 2 && branch.num == 2) {
                // case 3a - no action
            }
        }
    // file creation - case 3a-b and 4a-b
    // loop over tmpCurHeadBlobMap and tmpBranchHeadBlobMap to find newly created files
        for (Map.Entry<String, String> remainingFileInCurHead: tmpCurHeadBlobMap.entrySet()) {
            String fileName = remainingFileInCurHead.getKey();
            String blobHash = remainingFileInCurHead.getValue();
            String branchBlobHash = tmpBranchHeadBlobMap.get(fileName);
            if (branchBlobHash == null) { // case 4a - no action
            } else {
                // case 3a - no action
                if (!blobHash.equals(branchBlobHash)) {
                    // case 3b - merge conflict
                    conflict = handleMergeConflict(fileName,
                            readContentsAsString(join(BLOBS, blobHash)),
                            readContentsAsString(join(BLOBS, branchBlobHash)));
                }
                tmpBranchHeadBlobMap.remove(fileName);
            }
        }
        for (Map.Entry<String, String> remainingFileInBranchHead: tmpBranchHeadBlobMap.entrySet()) {
            // case 4b
            // change to version in the given branch (copy from BLOBS to CWD) and stage change
            String fileName = remainingFileInBranchHead.getKey();
            try {
                Files.copy(join(BLOBS, remainingFileInBranchHead.getValue()).toPath(),
                        join(CWD, fileName).toPath(), REPLACE_EXISTING);
                Files.copy(join(CWD, fileName).toPath(),
                        join(STAGE, fileName).toPath(), REPLACE_EXISTING);
            } catch (IOException e) {
                throw Utils.error("IOException during file copy operation.");
            }
        }
        // commit the file
        if (conflict == 1) {
            System.out.println("Encountered a merge conflict.");
        }
        // merge commit
        Commit.makeMergeCommit(String.format("Merged %s into %s.", branchName, currentBranch),
                branchHeadHash);
        clearStage();
    }

    /** Print corresponding error and exit if any of the following criteria is met.
     *  1) Uncommitted changes
     *  2) File(s) not tracked by current head commit and present in CWD
     *  3) Non-existent branch name.
     *  4) Merging current branch with itself. */
    private static void checkMergeGeneralFailureCases(String branchName,
                                                      Map<String, String> curHeadBlobMap) {
        checkUncommittedChanges();
        for (String f : plainFilenamesIn(CWD)) {
            if (!curHeadBlobMap.containsKey(f)) {
                System.out.println("There is an untracked file in the way; delete it, "
                        + "or add and commit it first.");
                System.exit(0);
            }
        }
        if (!headMap.containsKey(branchName)) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }
        if (branchName.equals(currentBranch)) {
            System.out.println("Cannot merge a branch with itself.");
            System.exit(0);
        }
    }

    /** Compare file version of an entry in split point's blob map with the corresponding entry in
     *  the given blob map.
     *  Output: 0 if the file version on given blob map is the same as that of split point.
     *          1 if the file version on given blob map different from that of split point.
     *          2 if the file exists only in split point's blob map. */
    private static VersionComparator compareFile(Map.Entry<String, String> splitPointPair,
                                                 String fileName,
                                                 Map<String, String> givenBlobMap) {

        String blobHashInGiven = givenBlobMap.get(fileName);
        int num;
        if (blobHashInGiven == null) {
            num = 2;
            return new VersionComparator(num, null);
        }
        String blobHashInSplitPoint = splitPointPair.getValue();
        num = (blobHashInSplitPoint.equals(blobHashInGiven)) ? 0 : 1;
        givenBlobMap.remove(fileName);
        return new VersionComparator(num, blobHashInGiven);
    }

    /** Handles merge conflicts. Write a file with the conflicted content to CWD. Stage the file.
     *  Return 1 meaning that a conflict exists. */
    private static int handleMergeConflict(String fileName, String curBlobContent,
                                           String branchBlobContent) {
        String conflictedFileContent = "<<<<<<< HEAD" + System.lineSeparator()
                                        + curBlobContent
                                        + "=======" + System.lineSeparator()
                                        + branchBlobContent
                                        + ">>>>>>>\n";
        try {
            writeContents(join(CWD, fileName), conflictedFileContent);
            Files.copy(join(CWD, fileName).toPath(),
                    join(STAGE, fileName).toPath(), REPLACE_EXISTING);
        } catch (IOException e) {
            throw Utils.error("IOException during file operation.");
        }
        return 1;
    }

    /** Loop over all files in STAGE to print out a list of files staged for addition and a list of
     *  files staged for removal. Listed in lexicographical order. */
    static private void displayStagedFiles(List<String> filesInStage) {
        StringBuilder stagedFiles = new StringBuilder();
        StringBuilder filesStagedForRemoval = new StringBuilder();
        for (String f : filesInStage) {
            if (f.length() > keyStringLen
                    && f.substring(0, keyStringLen).equals(Repository.keyString)) {
                filesStagedForRemoval.append(f.substring(keyStringLen));
                filesStagedForRemoval.append("\n");
            } else {
                stagedFiles.append(f);
                stagedFiles.append("\n");
            }
        }
        System.out.println("=== Staged Files ===");
        System.out.println(stagedFiles);
        System.out.println("=== Removed Files ===");
        System.out.println(filesStagedForRemoval);
    }

    /** Loop over headMap and print all branch names in lexicographical order.
     *  Add an asterisk in front of the name of current branch. */
    static private void displayBranchInfo() {
        System.out.println("=== Branches ===");
        for (Map.Entry<String, String> branchPair : headMap.entrySet()) {
            if (branchPair.getKey().equals(currentBranch)) {
                System.out.println("*".concat(branchPair.getKey()));
            } else {
                System.out.println(branchPair.getKey());
            }
        }
        System.out.println();
    }

    /** Loop over blobMap of current commit and blobMap of STAGE to print out names of files
     *  that satisfy any one of the following criteria:
     *      1) file version in CurCommit different from file version in CWD, file not staged
     *      2) file version in STAGE different from file version in CWD
     *      3) exists in STAGE but deleted from CWD
     *      4) tracked in current commit but deleted from CWD; file not staged for removal */
    static private void displayModificationsNotStagedForCommit(List<String> filesInCWD,
                                                       List<String> filesInStage,
                                                       Map<String, String> curCommitBlobMap) {

        System.out.println("=== Modifications Not Staged For Commit ===");
        // Create blobMap for CWD and STAGE
        Map<String, String> cwdBlobMap = new HashMap<>();
        for (String f : filesInCWD) {
            String blobHash = sha1(readContents(new File(f)));
            cwdBlobMap.put(f, blobHash);
        }
        Map<String, String> stageBlobMap = new HashMap<>();
        for (String sf : filesInStage) {
            String sBlobHash = sha1(readContents(join(STAGE, sf)));
            stageBlobMap.put(sf, sBlobHash);
        }
        // Create set to store unique file names that satisfy any criteria
        Set<String> out = new HashSet<>();

        for (Map.Entry<String, String> blobPair : stageBlobMap.entrySet()) {
            String file = blobPair.getKey();
            if (file.length() > keyStringLen && file.substring(0, keyStringLen).equals(keyString)) {
                continue;
            }
            String blobHash = blobPair.getValue();
            if (!cwdBlobMap.containsKey(file)) {
                out.add(file + " (deleted)");
            } else if (!blobHash.equals(cwdBlobMap.get(file))) {
                out.add(file + " (modified)");
            }
        }

        for (Map.Entry<String, String> blobPair : curCommitBlobMap.entrySet()) {
            String file = blobPair.getKey();
            String blobHash = blobPair.getValue();
            if (!cwdBlobMap.containsKey(file)
                    && !stageBlobMap.containsKey(keyString.concat(file))) {
                out.add(file + " (deleted)");
            }
            if (cwdBlobMap.containsKey(file) && !blobHash.equals(cwdBlobMap.get(file))
                     && !stageBlobMap.containsKey(file)) {
                out.add(file + " (modified)");
            }
        }
        // Sort and print output list
        List<String> output = new LinkedList<>(out);
        Collections.sort(output);
        for (String i : output) {
            System.out.println(i);
        }
        System.out.println();
    }

    /** Print out all files in CWD that are neither staged for addition nor
     *  tracked in current commit, in lexicographical order. Also include files staged
     *  for removal but then re-created without Gitlet's knowledge. */
    static private void displayUntrackedFiles(List<String> filesInCWD, List<String> filesInStage,
                                      Map<String, String> curCommitBlobMap) {
        System.out.println("=== Untracked Files ===");
        for (String f : filesInCWD) {
            if (!curCommitBlobMap.containsKey(f) && !filesInStage.contains(f)) {
                System.out.println(f);
            }
        }
        System.out.println();
    }

    /** Traverse up the commit tree (from current head to initial commit) recursively.
     *  Print out the commit id, time and commit message for each commit traversed. */
    private static void logHelper(String curCommitHash) {
        if (curCommitHash == null) {
            return;
        }
        Commit curCommit = getCommitFromHash(curCommitHash);
        displayCommitInfo(curCommitHash, curCommit);
        logHelper(curCommit.getParentCommitHash());
    }

    /** Display information of a commit. */
    private static void displayCommitInfo(String hash, Commit commit) {
        System.out.println("===");
        System.out.println("commit ".concat(hash));
        if (commit.getSecondParentCommitHash() != null){
            System.out.println("Merge: " + commit.getParentCommitHash().substring(0, 7) + " "
                    + commit.getSecondParentCommitHash().substring(0, 7));
        }
        DateFormat dateFormat = new SimpleDateFormat("E MMM dd HH:mm:ss yyyy Z");
        System.out.println("Date: ".concat(dateFormat.format(commit.getCommitDate())));
        System.out.println(commit.getCommitMsg().concat("\n"));
    }

    /* ============================================================================================
       General helper functions
       ============================================================================================
     */

    static void checkUncommittedChanges() {
        if (!plainFilenamesIn(STAGE).isEmpty()) {
            System.out.println("You have uncommitted changes.");
            System.exit(0);
        }
    }

    /** Clear the STAGE directory */
    private static void clearStage() {
        for (String f : plainFilenamesIn(STAGE)) {
            if (!join(STAGE, f).delete()) {
                throw Utils.error("File cannot be deleted");
            }
        }
    }

    /** Get hash string of input branch. Check if headMap is empty.
     *  If empty, deserialize headMap file. */
    static String getHeadHash(String branchName) {
        importHeadMap();
        String out = headMap.get(branchName);
        if (out == null) {
            System.out.println("No such branch exists.");
            System.exit(0);
        }
        return out;
    }

    /** Get hash string of current branch head. Check if headMap is empty.
     *  If empty, deserialize headMap file. */
    static String getHeadHash() {
        importHeadMap();
        importCurrentBranch();
        return headMap.get(currentBranch);
    }

    /** Get a commit object from its hash. Return null if input is null.
     * Cache the (hash, commit) pair if it has not been done so. */
    static Commit getCommitFromHash(String hash) {
        if (hash == null) {
            return null;
        }
        if (hash.length() == 8) {
            if (Commit.shortCommitMap.isEmpty()) {
                Commit.shortCommitMap = readObject(join(GITLET_DIR,"shortCommitIdMap"),
                    StringHashMap.class);
            }
            hash = Commit.shortCommitMap.get(hash);
            if (hash == null) {
                System.out.println("No commit with that id exists.");
                System.exit(0);
            }
        }
        if (!Commit.commitCache.containsKey(hash)) {
            try {
                Commit targetCommit = readObject(join(Commit.COMMITS, hash), Commit.class);
                Commit.commitCache.put(hash, targetCommit);
                return targetCommit;
            } catch (IllegalArgumentException iae) {
                System.out.println("No commit with that id exists.");
                System.exit(0);
            }
        }
        return Commit.commitCache.get(hash);
    }

    static void createFile(File f) {
        try {
            f.createNewFile();
        } catch (IOException e) {
            throw Utils.error("Encounter IOException when creating new file ((%s))", f);
        }
    }

    /** Import headMap from .gitlet directory if it is empty. HeadMap is empty when initialized. */
    static Map<String, String> importHeadMap() {
        if (headMap.isEmpty()) {
            headMap = readObject(join(GITLET_DIR, "headMap"), StringTreeMap.class);
        }
        return headMap;
    }

    static String importCurrentBranch() {
        currentBranch = readContentsAsString(join(GITLET_DIR, "currentBranch"));
        return currentBranch;
    }

    /* ============================================================================================
       Helper Classes
       ============================================================================================
    */

    /** Helper class for finding the last common ancestor of current branch and the given branch
     *  during a merge operation. */
    private static class SplitPointFinder {

        String curHeadHash;
        String branchHeadHash;
        String branchName;
        Map<String, Integer> currentCommitDigraph = new HashMap<>();
        Map<String, Integer> commonAncestorCommitCandidates = new HashMap<>();

        SplitPointFinder(String curHeadHash, String branchHeadHash, String branchName) {
            this.curHeadHash = curHeadHash;
            this.branchHeadHash = branchHeadHash;
            this.branchName = branchName;
        }

        /** Get commit hash of the last common ancestor commit of current and given branch. */
        String getSplitPointOfBranches() {
            // exit immediately if the two branches share the same head commit
            if (curHeadHash.equals(branchHeadHash)) {
                System.exit(0);
            }
            // create a set of commits in current branch containing from current commit to init
            // commit
            scanCommitOnCurBranch(curHeadHash, 0);
            // populate a set of candidates for last common ancestor commits
            findCommonAncestorsCommits(branchHeadHash);
            if (commonAncestorCommitCandidates.isEmpty()) {
                System.out.println("Error: cannot locate split points");
                System.exit(0);
            }
            String splitPointHash = null;
            double cmp = Double.NEGATIVE_INFINITY;
            for (String candidate : commonAncestorCommitCandidates.keySet()) {
                if (commonAncestorCommitCandidates.get(candidate) > cmp) {
                    splitPointHash = candidate;
                    cmp = commonAncestorCommitCandidates.get(candidate);
                }
            }
            return splitPointHash;
        }

        /** Populate a set of commits on current branch, starting from head commit to
         *  init commit, and store the commits with associated priority in a map. Scan through all
         *  parent commits. Priority is 0 at the current branch head and decrease towards init
         *  commit. Print notice and exit if head of given branch is an ancestor of that of
         *  current branch */
        void scanCommitOnCurBranch(String hash, int counter) {
            if (hash == null) {
                return;
            }
            if (hash.equals(branchHeadHash)) {
                System.out.println("Given branch is an ancestor of the current branch.");
                System.exit(0);
            }
            currentCommitDigraph.put(hash, counter);
            scanCommitOnCurBranch(getCommitFromHash(hash).getParentCommitHash(), counter - 1);
            scanCommitOnCurBranch(getCommitFromHash(hash).getSecondParentCommitHash(), counter - 1);
        }

        /** Populate a map indicating potential candidates for the last common ancestor commit
         *  between current and given branch. Checkout given branch, print notice and exit if head
         *  of current branch is an ancestor of that of given branch. Accept abbreviated commit
         *  id (i.e. first 8 characters of full id). */
        void findCommonAncestorsCommits(String hash) {
            if (hash == null) {
                return;
            }
            if (hash.equals(curHeadHash)) {
                // fast-forward current branch
                checkoutBranch(branchName);
                System.out.println("Current branch fast-forwarded.");
                System.exit(0);
            }
            if (currentCommitDigraph.containsKey(hash)) {
                commonAncestorCommitCandidates.put(hash, currentCommitDigraph.get(hash));
                return;
            }
            findCommonAncestorsCommits(getCommitFromHash(hash).getParentCommitHash());
            findCommonAncestorsCommits(getCommitFromHash(hash).getSecondParentCommitHash());
        }
    }

    /** Helper class of merge command */
    private static class VersionComparator{
        int num;
        String blobHash;

        VersionComparator(int num, String blobHash){
            this.num = num;
            this.blobHash = blobHash;
        }
    }

    static class StringTreeMap extends TreeMap<String, String> {}

    static class StringHashMap extends HashMap<String, String> {}
}
