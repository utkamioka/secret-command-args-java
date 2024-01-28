package org.example;

import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        SecretCommandArgs cmd = SecretCommandArgs.builder()
            .add("openssl")
            .add("enc")
            .add("-pass")
            .add("pass:%s", "secret")
            .build();

        System.out.println(cmd);
        // "openssl enc -pass:****"

        System.out.println(Arrays.toString(cmd.toArgs()));
        // ["openssl", "enc", "-pass:secret"]

        System.out.println(cmd.mask("<password>"));
        // "openssl enc -pass:<password>"

        System.out.println(cmd.alt("encrypt message..."));
        // "encrypt message..."
    }
}
