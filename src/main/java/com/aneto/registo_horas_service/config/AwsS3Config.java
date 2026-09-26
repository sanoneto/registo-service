package com.aneto.registo_horas_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
public class AwsS3Config {

    @Value("${spring.cloud.aws.s3.endpoint}")
    private String s3Endpoint;

    @Value("${spring.cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${spring.cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${spring.cloud.aws.region.static:us-east-1}")
    private String region;

    @Value("${spring.cloud.aws.s3.path-style-access:true}")
    private boolean pathStyleAccess;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .endpointOverride(URI.create(s3Endpoint))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyleAccess)
                        .build())
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .build();
    }
}