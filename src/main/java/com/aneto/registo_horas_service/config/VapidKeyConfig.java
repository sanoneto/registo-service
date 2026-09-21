package com.aneto.registo_horas_service.config;

import jakarta.annotation.PostConstruct;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.context.annotation.Configuration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
/*
@Configuration
public class VapidKeyConfig {

    @PostConstruct
    public void generateVapidKeys() {
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("ECDH", "BC");
            keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            // FORMA MANUAL (Sem usar o Utils.encode que está a dar erro)
            byte[] publicKey = ((org.bouncycastle.jce.interfaces.ECPublicKey) keyPair.getPublic()).getQ().getEncoded(false);
            byte[] privateKey = ((org.bouncycastle.jce.interfaces.ECPrivateKey) keyPair.getPrivate()).getD().toByteArray();

            System.out.println("\n=========================================");
            System.out.println("   CHAVES VAPID GERADAS COM SUCESSO    ");
            System.out.println("=========================================");
            System.out.println("PUBLIC KEY (Copia para o React):");
            System.out.println(Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey));
            System.out.println("\nPRIVATE KEY (Guarda no application.properties):");
            System.out.println(Base64.getUrlEncoder().withoutPadding().encodeToString(privateKey));
            System.out.println("=========================================\n");

        } catch (Exception e) {
            System.err.println("Erro ao gerar chaves VAPID: " + e.getMessage());
        }
    }
}

 */