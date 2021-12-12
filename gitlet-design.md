# Gitlet Design Document

**Name**: Jeffrey Fung

***
## Classes and Data Structures
***

### Main
Entry point to our program. Based on first command line argument, trigger corresponding methods in `GitletRepo`. Also validates number of arguments.

#### Fields
Has no fields and hence no associated state.

### GitletRepo
Handles most commands and main logic. 
* set up persistence - create `.gitlet`, `stage`, `commits`, `blobs` folders
* read/write commit / blob objects to directory
* maintain mapping between commit / blob hash str and runtime objects 
* additional error checking


Some operations and serializations about commits and blobs deferred to **commit** and **blob** classes.

#### Fields
1. `static final File CWD = new File(System.getProperty("user.dir"))`
2. `static final File GITLET_REPO = Utils.join(CWD, ".gitletRepo")`\
   Folder for holding `.gitlet`, `stage`, `commits`, `blobs`.
3. `static final File STAGE = new File(Utils.join(GITLET_REPO, "stage"))`\
   Folder for holding staged files.
4. `static final File BLOBS = join(GITLET_DIR, "blobs")`\
   Folder for holding file contents (blobs).
4. `static Map<str, str> headMap`\
   Map from branch name to hash of commit head of that branch.
5. `static String currentBranch = "master"`\
   Name of current branch. Default: master.
6. `static Map<String, String> blobToFileNameMap`
   Map from blob hash to file name. Useful in restoring original file name.
   
### Commit
This class represents a `commit` made in a repo. `commit` contains a reference to parent commit (only first parent in case of merge) such that it can be traced back to initial commit. Contains methods for serialization of commit objects. Maintains a cache map from commit hash to commit object. 

#### Fields 

1. `static final File COMMITS = Utils.join(GITLET_REPO, "commits")`\
   Folder for holding commit objects.
2. `static transient Map<str, Commit> commitCache`\
   Map that enables quick access to a runtime commit object from a commit hash string. Not serialized.
3. `private String commitMsg`\
   Metadata: Commit message string.
4. `private Date commitDate`\
   Metadata: Commit date.
5. `private Map<String, String> blobMap`\
   Contains every file tracked in this commit. Map from file name to blob hash string.
6. `private String parentCommitHash`\
   Hash string of (first) parent commit. Can be traced back to initial commit.
7. `private String secondParentCommitHash`\
   Hash string of second parent commit. Null for non-merge commit.
8. `private transient Commit parentCommit`\
   Parent commit object. Not serialized.
9. `private transient Commit secondParentCommit`\
   Second parent commit object. Not serialized.

### Blob (*deprecated*)
This class represents a `blob` (version of file content). Each file can have multiple `blobs` (multiple versions). Contains methods for serialization of `blob` objects. Maintains a cache map from blob hash to runtime blob object. 
#### Fields

1. `static final File BLOBS = Utils.join(GITLET_REPO, "blobs")`\
   Folder for holding blob objects.
2. `static Map<str, blob> blobCache`\
   Map that enables quick access to a runtime blob object from a blob hash string.

### Staged Files???

#### Fields

***
## Algorithms
***

### GitletRepo

+ `public static void initRepo()`\
   Call `setupPersistence` to create folders holding `.gitlet`, `stage`, `commits`, `blobs`. Make an initial commit by calling `makeInitCommit`. Print error if `.gitlet` already exists.
+ `private static void setupPersistance()`\
   Helper method of `initRepo` to create folders.
+ `private static void makeInitCommit()`\
   Helper method of `initRepo` to create a commit object with `commitDate = 00:00:00 UTC, Thursday, 1 January 1970`, `commitMsg = "initial commit"` and no parent. Modify headMap and commitCache. Serialize commit object with `serializeCommit`.
+ `static void add(str file)`\
   Find the file in cwd (how?). Print error if the file does not exist.
   If the file's hash exists in current commit's `blobMap`, do not stage and remove same-named file from `stage` (if any).
   If the file's hash exists in `stage`, do nothing.
   Overwrites same-named file in  `stage` if they contain different hashes.
   Update blobCache if file is staged.
+ `static void makeCommit(str message)`\
   Make a commit... 

### Commit
+ `void serializeCommit()`\
   Serialize this commit object. Calls `...`.
***
## Persistence
***
![img_2.png](img_2.png)
