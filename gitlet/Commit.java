package gitlet;


import static gitlet.Utils.*;

import java.io.File;
import java.io.Serializable;
import java.util.*;

/** Represents a gitlet commit object.
 *  Allows commit object to be hashed and serialized to file named with the hash.
 *  @author Jeffrey Fung
 */
public class Commit implements Serializable {
    /**
     *
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */

    /** The commits directory */
    static final File COMMITS = join(Repository.GITLET_DIR, "commits");
    /** The message of this Commit. */
    private final String commitMsg;
    /** Commit date */
    private final Date commitDate;
    /** Map from file name to blob hash. Indicates which version of content does the commit
    * tracks. */
    private final Map<String, String> blobMap;
    /** Hash of (first) parent commit. Can be used to trace back to initial commit */
    private final String parentCommitHash;
    /** Hash of second parent commit. Null for non-merge commit */
    private String secondParentCommitHash = null;
    /** Map from first 8 characters of commit hash to its full version */
    static Map<String, String> shortCommitMap = new Repository.StringHashMap();
    /** Map from commit hash to runtime commit object. Not serialized */
    static transient Map<String, Commit> commitCache = new HashMap<>();
    /** Parent commit object. Not serialized. */
    private transient Commit parentCommit;
    /** Second parent commit object. Not serialized. */
    private transient Commit secondParentCommit;

    public Commit(String commitMsg, Date commitDate, String parentCommitHash, Map<String,
            String> blobMap) {
        this.commitMsg = commitMsg;
        this.commitDate = commitDate;
        this.parentCommitHash = parentCommitHash;
        this.blobMap = blobMap;
    }

    /** Create a commit object with commitDate = 00:00:00 UTC, Thursday, 1 January 1970,
     * commitMsg = "initial commit" and no parent. */
    static void makeInitCommit() {
        Date initDate = new Date(0);
        Commit initCommit = new Commit("initial commit", initDate,
                null, new Repository.StringTreeMap());
        Repository.currentBranch = "master";
        commitHelper(initCommit);
        writeContents(join(Repository.GITLET_DIR, "currentBranch"),
                Repository.currentBranch);
    }

    /** Create a new commit. Its parent commit is the HEAD of current branch and is represented by
     *  hash. Record the time of commit. Its blobMap is identical to the parent except for files
     *  in STAGE. If parent commit does not contain a blobMap (i.e. init commit), the new blobMap
     *  contains only reference to files in STAGE in the format of (file name -> blob hash of
     *  serialized file content). */
    public static void makeCommit(String commitMsg) {
        commitHelper(handleCommit(commitMsg));
    }

    /** Make merge commit. */
    static void makeMergeCommit(String commitMsg, String secondParentCommitHash) {
        Commit c = handleCommit(commitMsg);
        c.secondParentCommitHash = secondParentCommitHash;
        commitHelper(c);
    }

    private static Commit handleCommit(String commitMsg) {
        List<String> filesInStage = plainFilenamesIn(Repository.STAGE);
        if (filesInStage.size() == 0) {
            System.out.println("No changes added to the commit.");
            System.exit(0);
        }
        Date curDate = new Date();
        String parentCommitHash = Repository.getHeadHash();
        Commit parentCommit = Repository.getCommitFromHash(parentCommitHash);
        Map<String, String> parentBlobMap = parentCommit.getBlobMap();
        Map<String, String> commitBlobMap = new Repository.StringTreeMap();
        if (parentBlobMap != null) {
            commitBlobMap.putAll(parentBlobMap);
        }
        for (String f : filesInStage) {
            // check stage for removal by checking if fileName starts with "-del-"
            // if so remove corresponding key from commitBlobMap
            // if fileName does not exist in current commit (i.e. commitBlobMap)
            // -> address exception in Repository.remove
            if (f.length() > Repository.keyStringLen
                    && f.substring(0, Repository.keyStringLen).equals(Repository.keyString)) {
                commitBlobMap.remove(f.substring(Repository.keyStringLen));
                continue;
            }
            byte[] blobBytes = readContents(join(Repository.STAGE, f));
            String blobHash = sha1(blobBytes);
            writeContents(join(Repository.BLOBS, blobHash), blobBytes);
            commitBlobMap.put(f, blobHash);
        }
        Commit curCommit = new Commit(commitMsg, curDate, parentCommitHash, commitBlobMap);
        curCommit.parentCommit = parentCommit;
        return curCommit;
    }

    /** Private helper method to dump commit object into newly created file
     * named by serialized byte array's SHA-1 hash. Also update the head commit
     * of current branch and cache the commit object for quick runtime access. */
    private static void commitHelper(Commit c) {
        byte[] commitByte = serialize(c);
        String commitHash = sha1(commitByte);
        writeContents(join(COMMITS, commitHash), commitByte);

        if (join(Repository.GITLET_DIR, "shortCommitIdMap").exists()) {
            shortCommitMap = readObject(join(Repository.GITLET_DIR, "shortCommitIdMap"),
                    Repository.StringHashMap.class);
        }
        shortCommitMap.put(commitHash.substring(0, 8), commitHash);
        writeObject(join(Repository.GITLET_DIR, "shortCommitIdMap"),
                (Serializable) shortCommitMap);
        if (Repository.currentBranch == null) {
            Repository.currentBranch = readContentsAsString(join(Repository.GITLET_DIR,
                    "currentBranch"));
        }
        Repository.headMap.put(Repository.currentBranch, commitHash);
        writeObject(join(Repository.GITLET_DIR, "headMap"), (Serializable) Repository.headMap);
        commitCache.put(commitHash, c);
    }

    public Map<String, String> getBlobMap() { return this.blobMap; }

    public Date getCommitDate() { return this.commitDate; }

    public String getCommitMsg() { return this.commitMsg; }

    public String getParentCommitHash() { return this.parentCommitHash; }

    public String getSecondParentCommitHash() { return this.secondParentCommitHash; }

}
