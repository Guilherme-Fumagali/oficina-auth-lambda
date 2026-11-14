package com.oficina.auth.handler;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.ArrayList;
import java.util.List;

class ContextoFake implements Context {

    final List<String> linhas = new ArrayList<>();

    String log() {
        return String.join("\n", linhas);
    }

    @Override public LambdaLogger getLogger() {
        return new LambdaLogger() {
            @Override public void log(String message) { linhas.add(message); }
            @Override public void log(byte[] message) { linhas.add(new String(message)); }
        };
    }
    @Override public String getAwsRequestId() { return "test"; }
    @Override public String getLogGroupName() { return "test"; }
    @Override public String getLogStreamName() { return "test"; }
    @Override public String getFunctionName() { return "oficina-auth"; }
    @Override public String getFunctionVersion() { return "1"; }
    @Override public String getInvokedFunctionArn() { return "arn:test"; }
    @Override public CognitoIdentity getIdentity() { return null; }
    @Override public ClientContext getClientContext() { return null; }
    @Override public int getRemainingTimeInMillis() { return 10_000; }
    @Override public int getMemoryLimitInMB() { return 512; }
}
