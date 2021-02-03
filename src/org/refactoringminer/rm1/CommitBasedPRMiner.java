package org.refactoringminer.rm1;

import org.kohsuke.github.*;
import org.refactoringminer.api.RefactoringHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CommitBasedPRMiner implements PRMiner {
    private static final Logger logger = LoggerFactory.getLogger(GitHistoryRefactoringMinerImpl.class);
    private final GitHistoryRefactoringMinerImpl refactoringMiner;

    public CommitBasedPRMiner(GitHistoryRefactoringMinerImpl refactoringMiner) {
        this.refactoringMiner = refactoringMiner;
    }

    public void detectAtPullRequest(String cloneURL, int pullRequestId, RefactoringHandler handler, int timeout) throws IOException {
        GitHub gitHub = refactoringMiner.connectToGitHub();
        String repoName = refactoringMiner.extractRepositoryName(cloneURL);
        GHRepository repository = gitHub.getRepository(repoName);
        GHPullRequest pullRequest = repository.getPullRequest(pullRequestId);

        PagedIterable<GHPullRequestCommitDetail> commits = pullRequest.listCommits();
        for(GHPullRequestCommitDetail commit : commits) {
            refactoringMiner.detectAtCommit(cloneURL, commit.getSha(), handler, timeout);
        }
    }

}
