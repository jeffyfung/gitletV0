# gitletV0

# Overview

Gitlet is Git but simpler and smaller. It serves to demonstrate the basic functionality of git, such as staging files,
committing files, checking out past commits, checking commit logs, introducing and switching between branches, etc. It
can also interact with a separate repository in a manner similar to how Git interacts with Github. Blobs are
serialized to byte arrays for ease of storage. Blobs and data of commits are represented and can be accessed by their
SHA-1 hash to avoid collision of names and allow immutability. Just like Git, blobs and commits can be accessed by 
either their full hash (40 char) or shortened hash which is the just first 6 char. Written in Java (JDK 15.0.2) and 
based on a course project of UC Berkeley [CS61B](https://sp21.datastructur.es/materials/proj/proj2/proj2). 

# Future Features
There is a plan to build a corresponding website to aid visual learners.

# Project Structure
Java files of the program are located in [`gitlet/`](./gitlet/). Corresponding class files are located in 
[`gitlet/classes/gitlet/`](./gitlet/classes/gitlet/). 

`Main.java` : entry point of the program.

`Repository.java` : where the main logic of gitlet's functions reside.

`RemoteRepo.java` : allows setting up a separate repository ("remote repository") and interaction between local and
remote repository, just like Git and Github.

`Commit.java` : allows creation and access of commits.

`Utils.java` : utility for serialization, hashing and file writing/reading.

Files related to testing reside in `testing/`.

# Commands

### init

Run the following command.
```
java -cp [LOCATION_OF_GITLET_CLASSES] gitlet.Main init
```

Description: 

Creates a new Gitlet version-control system in `.gitlet` with an initial commit. The initial commit contains no
file and has the commit message `initial commit`. It will have a single branch: `master`, which initially points to this
initial commit, and `master` will be the current branch. The timestamp for this initial commit is Unix Epoch time 0
(00:00:00 UTC, Thursday, 1 January 1970). All commits should trace back to this initial commit. 

### add

Run the following command.
```
java -cp [LOCATION_OF_GITLET_CLASSES] gitlet.Main add [FILE_NAME]
```

Description: 

Stages an existing file in working directory for a future commit. Staging an already-staged file overwrites
the previous entry in the staging area with the new contents. The staging area is located in `.gitlet/stage`. No
action if the version of file to be staged is identical to the version in the current commit. Different from git, only 
one file can be added at a time.

### commit

Run the following command.
```
java -cp [LOCATION_OF_GITLET_CLASSES] gitlet.Main commit [FILE_NAME]
```

Description: 

Creates a new commit. Saves a snapshot of tracked files in the current commit and staging area so that they
can be restored at a later time. A commit will not alter contents of a file. Instead, a commit will only update the 
tracking of versions of files so that Gitlet knows which version of a file will be used in certain commit. A commit will
save and start tracking any files that were staged for addition but weren’t tracked by its parent. 

All versions of files are stored in `.gitlet/blobs/`. All commits are stored in `.gitlet/commits/`. 

Files tracked in the current commit can be untracked in the new commit by invoking the `rm` command (described below).

Each commit is idendified by its SHA-1 hash and includes parent reference, log message, commit time and information
about tracking of file versions.

### rm

Run the following command.
```
java -cp [LOCATION_OF_GITLET_CLASSES] gitlet.Main rm [FILE_NAME]
```

Description: 

Unstages the file if it is currently staged for addition. If the file is tracked in the current commit, 
stage it for removal and remove the file from the working directory if the user has not already done so (do not remove 
it unless it is tracked in the current commit).

### log

Run the following command.
```
java -cp [LOCATION_OF_GITLET_CLASSES] gitlet.Main log
```

Description: 

Starting at the current head commit, display information about each commit backwards along the commit tree 
until the initial commit, following the first parent commit links, ignoring any second parents found in merge commits.
For each commit, the commit id, date and commit message are displayed.

An example of log is shown below:

```
Example:
$ java -cp ../../gitlet/classes gitlet.Main log
===
commit 228c71e9efb54bbc588d28870a4760a5b7fc295d
Date: Sun Dec 12 14:34:09 2021 +0800
add contentV0.txt

===
commit cc36cb13ce7cbafb11e83c679475bb54108398f0
Date: Thu Jan 01 08:00:00 1970 +0800
initial commit
```

### global-log

Run the following command.
```
java -cp [LOCATION_OF_GITLET_CLASSES] gitlet.Main global-log
```

Description: 

Like log, except displays information about all commits ever made including all parent commits. The order 
of the commits does not matter.

### find

Run the following command.
```
java -cp [LOCATION_OF_GITLET_CLASSES] gitlet.Main find [COMMIT_MESSAGE]
```

Description: 

Prints out the ids of all commits that have the given commit message, one per line. If there are multiple
such commits, it prints the ids out on separate lines. The commit message should be a single operand.

### status

Run the following command.
```
java -cp [LOCATION_OF_GITLET_CLASSES] gitlet.Main status
```

Description: 

Displays a list of existing branches and marks the current branch with a `*`. Also displays what files
have been staged for addition or removal. An example of the exact format it should follow is as follows.

```
Example:
$ java -cp ../../gitlet/classes gitlet.Main status
=== Branches ===
*master
other-branch
  
=== Staged Files ===
wug.txt
wug2.txt
  
=== Removed Files ===
goodbye.txt
  
=== Modifications Not Staged For Commit ===
junk.txt (deleted)
wug3.txt (modified)
  
=== Untracked Files ===
random.stuff
  
```

### checkout

Run the one of following commands.
```
1. java -cp [LOCATION_OF_GITLET_CLASSES] gitlet.Main checkout -- [FILE_NAME]
2. java -cp [LOCATION_OF_GITLET_CLASSES] gitlet.Main checkout [COMMIT_ID] -- [FILE_NAME]
3. java -cp [LOCATION_OF_GITLET_CLASSES] gitlet.Main checkout [BRANCH_NAME]
```

Descriptions:

1. checkout the specified file in the head commit by putting that version of the file in cwd.
2. checkout the specified file from the specified commit by putting that version of the file in cwd.
3. checkout all files from the head commit of specified branch by putting those files in cwd.

### branch

Run the following command.
```
java -cp [LOCATION_OF_GITLET_CLASSES] gitlet.Main branch [BRANCH_NAME]
```

Description:

Creates a new branch with the given name, and points it at the current head commit. Different from Git, this command
does not immediately switch to the newly created branch. Throws an exception if a branch with the given branch name
already exists.

### rm-branch

Run the following command.
```
java -cp [LOCATION_OF_GITLET_CLASSES] gitlet.Main rm-branch [BRANCH_NAME]
```

Description: 

Deletes the branch with the given name.

### reset

Run the following command.
```
java -cp [LOCATION_OF_GITLET_CLASSES] gitlet.Main reset [COMMIT_ID]
```

Description: 

Checkout all the files tracked by the given commit. Removes tracked files that are not present in that commit. Also
moves the current branch’s head to that commit node. The staging area is cleared.

### merge

Run the following command.
```
java -cp [LOCATION_OF_GITLET_CLASSES] gitlet.Main merge [BRANCH_NAME]
```

Description: 

Merges files from the given branch into the current branch. Instead of only displaying conflicts in places where both 
files have changed since the split point (commit) during merges (as Git does), Gitlet commits the conflict message. A 
separate commit is needed to resolve conflicts. Gitlet also has a different way to decide which of multiple possible 
split points to use.
