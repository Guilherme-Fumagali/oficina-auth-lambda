package com.oficina.auth;

import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;

import java.net.URI;

public final class LocalStackCompartilhado {

    private static LocalStackContainer container;

    private LocalStackCompartilhado() {}

    public static synchronized LocalStackContainer container() {
        if (container == null) {
            container = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3"));
            container.start();
        }
        return container;
    }

    public static URI endpoint() {
        return container().getEndpoint();
    }

    public static <B extends AwsClientBuilder<B, C>, C> C cliente(B builder) {
        var localstack = container();
        return builder
            .endpointOverride(localstack.getEndpoint())
            .region(Region.of(localstack.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
            .build();
    }
}
