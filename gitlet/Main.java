package gitlet;

import gitlet.Utils;
import gitlet.GitletException;
import gitlet.Repository;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Jeffrey Fung
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            System.exit(0);
        }
        String firstArg = args[0];
        switch (firstArg) {
            case "init" -> {
                validateNumArgs("init", args, 1);
                Repository.initRepo();
            }
            case "add" -> {
                validateNumArgs("add", args, 2);
                Repository.checkForGitletDir();
                Repository.add(args[1]);
            }
            case "commit" -> {
                if (args.length == 1 || args[1].equals("")) {
                    System.out.println("Please enter a commit message.");
                    System.exit(0);
                }
                validateNumArgs("commit", args, 2);
                Repository.checkForGitletDir();
                Repository.commit(args[1]);
            }
            case "rm" -> {
                validateNumArgs("rm", args, 2);
                Repository.checkForGitletDir();
                Repository.remove(args[1]);
            }
            case "log" -> {
                validateNumArgs("log", args, 1);
                Repository.checkForGitletDir();
                Repository.log();
            }
            case "global-log" -> {
                validateNumArgs("global-log", args, 1);
                Repository.checkForGitletDir();
                Repository.globalLog();
            }
            case "find" -> {
                validateNumArgs("find", args, 2);
                Repository.checkForGitletDir();
                Repository.find(args[1]);
            }
            case "status" -> {
                validateNumArgs("status", args, 1);
                Repository.checkForGitletDir();
                Repository.status();
            }
            case "checkout" -> {
                Repository.checkForGitletDir();
                if (args.length == 1 || args.length > 4) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                else if (args.length == 2) {
                    Repository.checkoutBranch(args[1]);
                }
                else if (args[1].equals("--")) {
                    validateNumArgs("checkout", args, 3);
                    Repository.checkout(args[2]);
                }
                else if (args[2].equals("--")) {
                    validateNumArgs("checkout", args, 4);
                    Repository.checkout(args[1], args[3]);
                }
                else {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
            }
            case "branch" -> {
                validateNumArgs("branch", args, 2);
                Repository.checkForGitletDir();
                Repository.branch(args[1]);
            }
            case "rm-branch" -> {
                validateNumArgs("rm-branch", args, 2);
                Repository.checkForGitletDir();
                Repository.rmBranch(args[1]);
            }
            case "reset" -> {
                validateNumArgs("reset", args, 2);
                Repository.checkForGitletDir();
                Repository.reset(args[1]);
            }
            case "merge" -> {
                validateNumArgs("merge", args, 2);
                Repository.checkForGitletDir();
                Repository.merge(args[1]);
            }
            case "add-remote" -> {
                validateNumArgs("add-remote", args, 3);
                Repository.checkForGitletDir();
                RemoteRepo.addRemote(args[1], args[2]);
            }
            case "rm-remote" -> {
                validateNumArgs("rm-remote", args, 2);
                Repository.checkForGitletDir();
                RemoteRepo.rmRemote(args[1]);
            }
            case "push" -> {
                validateNumArgs("push", args, 3);
                Repository.checkForGitletDir();
                RemoteRepo.push(args[1], args[2]);
            }
            case "fetch" -> {
                validateNumArgs("fetch", args, 3);
                Repository.checkForGitletDir();
                RemoteRepo.fetch(args[1], args[2]);
            }
            case "pull" -> {
                validateNumArgs("pull", args, 3);
                Repository.checkForGitletDir();
                RemoteRepo.pull(args[1], args[2]);
            }
            default -> {
                System.out.println("No command with that name exists.");
                System.exit(0);
            }
        }
    }

    public static void validateNumArgs(String cmd, String[] args, int n) {
        if (args.length != n) {
            System.out.println("Incorrect operands.");
            System.exit(0);
        }
    }
}
