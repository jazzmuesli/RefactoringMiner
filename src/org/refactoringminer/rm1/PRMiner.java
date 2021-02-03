package org.refactoringminer.rm1;

import org.refactoringminer.api.RefactoringHandler;

import java.io.IOException;

public interface PRMiner {
    void detectAtPullRequest(String cloneURL, int pullRequestId, RefactoringHandler handler, int timeout) throws IOException;
}
