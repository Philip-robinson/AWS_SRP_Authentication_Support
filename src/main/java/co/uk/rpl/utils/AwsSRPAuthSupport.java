/**
 * This is a modified copy of AuthenticationHelper taken from
 *  https://github.com/aws-samples/aws-cognito-java-desktop-app/blob/master/src/main/java/com/amazonaws/sample/cognitoui/AuthenticationHelper.java
 *
 * SPDX-License-Identifier: MIT-0
 *
 * Copyright 2023 Ridgefield-Pennine Ltd.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 *
 */
package co.uk.rpl.utils;

import static java.lang.System.currentTimeMillis;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.getLogger;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeRequest;

/**
 * Private class for SRP client side math.
 */
public class AwsSRPAuthSupport {

    private static Logger log = getLogger(AwsSRPAuthSupport.class);

    public AwsSRPAuthSupport(Config config, String token, long expiresAt, BigInteger a, BigInteger A) {
        this.config = config;
        this.token = token;
        this.expiresAt = expiresAt;
        this.a = a;
        this.A = A;
    }

    private class Config {

        public Config(String companyId, String userPoolId,
                      String userPoolClientId, String region,
                      String secretKey, String username,
                      String password, long timeBeforeExpiryToRefresh) {
            this.companyId = companyId;
            this.userPoolId = userPoolId;
            this.userPoolClientId = userPoolClientId;
            this.region = region;
            this.secretKey = secretKey;
            this.username = username;
            this.password = password;
            this.timeBeforeExpiryToRefresh = timeBeforeExpiryToRefresh;
        }

        private final String companyId;
        private final String userPoolId;
        private final String userPoolClientId;
        private final String region;
        private final String secretKey;
        private final String username;
        private final String password;
        private final long timeBeforeExpiryToRefresh;

        public boolean hasSecretKey() {
            return secretKey != null && secretKey.length() > 0;
        }

        Region getRegion() {
            return Region.of(region);
        }

        @Override
        public String toString() {
            return "Config{" + "companyId=" + companyId
                   + ", userPoolId=" + userPoolId
                   + ", userPoolClientId=" + userPoolClientId
                   + ", region=" + region
                   + ", username=" + username
                   + ", password=" + password + '}';
        }

        private String getUserPoolId() {
            return userPoolId;
        }

        public String getCompanyId() {
            return companyId;
        }

        public String getUserPoolClientId() {
            return userPoolClientId;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public long getTimeBeforeExpiryToRefresh() {
            return timeBeforeExpiryToRefresh;
        }

    }
    private final Config config;
    private String token;
    private long expiresAt;
    private static final String HEX_N
            = "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1"
              + "29024E088A67CC74020BBEA63B139B22514A08798E3404DD"
              + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245"
              + "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
              + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D"
              + "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F"
              + "83655D23DCA3AD961C62F356208552BB9ED529077096966D"
              + "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B"
              + "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9"
              + "DE2BCBF6955817183995497CEA956AE515D2261898FA0510"
              + "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64"
              + "ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7"
              + "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6B"
              + "F12FFA06D98A0864D87602733EC86A64521F2B18177B200C"
              + "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB31"
              + "43DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF";
    private static final BigInteger N = new BigInteger(HEX_N, 16);
    private static final BigInteger g = BigInteger.valueOf(2);
    private static final BigInteger k;
    private static final int EPHEMERAL_KEY_LENGTH = 1024;
    private static final int DERIVED_KEY_SIZE = 16;
    private static final String DERIVED_KEY_INFO = "Caldera Derived Key";
    private static final ThreadLocal<MessageDigest> THREAD_MESSAGE_DIGEST
            = new ThreadLocal<MessageDigest>() {
        @Override
        protected MessageDigest initialValue() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new SecurityException("Exception in authentication", e);
            }
        }
    };
    private static final SecureRandom SECURE_RANDOM;

    static {
        try {
            SECURE_RANDOM = SecureRandom.getInstance("SHA1PRNG");

            MessageDigest messageDigest = THREAD_MESSAGE_DIGEST.get();
            messageDigest.reset();
            messageDigest.update(N.toByteArray());
            byte[] digest = messageDigest.digest(g.toByteArray());
            k = new BigInteger(1, digest);
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityException(e.getMessage(), e);
        }
    }

    private BigInteger a;
    private BigInteger A;

    /**
     *
     * @param companyId aws company identifier
     * @param userPoolId aws cognito user pool identifier
     * @param userPoolClientId aws cognito user pool client identifier
     * @param region aws region code
     * @param secretKey secret key for access to identity pool null or space
     * blank string if not required.
     * @param username I am username to access cognito
     * @param password I am password to access cognitot
     * @param timeBeforeExpiryToRefresh number of seconds before expiry of the
     * token when it should be considdered expired an a new one requested.
     */
    public AwsSRPAuthSupport(String companyId,
                             String userPoolId,
                             String userPoolClientId,
                             String region,
                             String secretKey,
                             String username,
                             String password,
                             long timeBeforeExpiryToRefresh) {
        config = new Config(companyId,
                            userPoolId,
                            userPoolClientId,
                            region,
                            secretKey,
                            username,
                            password,
                            timeBeforeExpiryToRefresh);
        do {
            a = new BigInteger(EPHEMERAL_KEY_LENGTH, SECURE_RANDOM).mod(N);
            A = g.modPow(a, N);
        } while (A.mod(N).equals(BigInteger.ZERO));
    }

    private BigInteger getA() {
        return A;
    }

    private byte[] getPasswordAuthenticationKey(String userId,
                                                String userPassword,
                                                BigInteger B,
                                                BigInteger salt) {
        // Authenticate the password
        // u = H(A, B)
        MessageDigest messageDigest = THREAD_MESSAGE_DIGEST.get();
        messageDigest.reset();
        messageDigest.update(A.toByteArray());
        BigInteger u = new BigInteger(1, messageDigest.digest(B.toByteArray()));
        if (u.equals(BigInteger.ZERO)) {
            throw new SecurityException("Hash of A and B cannot be zero");
        }

        // x = H(salt | H(poolName | userId | ":" | password))
        messageDigest.reset();
        messageDigest.update(config.getUserPoolId().split("_", 2)[1].getBytes(UTF_8));
        messageDigest.update(userId.getBytes(UTF_8));
        messageDigest.update(":".getBytes(UTF_8));
        byte[] userIdHash = messageDigest.digest(userPassword.getBytes(UTF_8));

        messageDigest.reset();
        messageDigest.update(salt.toByteArray());
        BigInteger x = new BigInteger(1, messageDigest.digest(userIdHash));
        BigInteger S = (B.subtract(k.multiply(g.modPow(x, N))).
                modPow(a.add(u.multiply(x)), N)).mod(N);

        Hkdf hkdf;
        try {
            hkdf = Hkdf.getInstance("HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityException(e.getMessage(), e);
        }
        hkdf.init(S.toByteArray(), u.toByteArray());
        byte[] key = hkdf.deriveKey(DERIVED_KEY_INFO, DERIVED_KEY_SIZE);
        return key;
    }

    public synchronized String getToken() {
        if (token == null
            || expiresAt < currentTimeMillis() - config.getTimeBeforeExpiryToRefresh() * 1000) {
            performSRPAuthentication();
        }
        return token;
    }

    /**
     * Method to orchestrate the SRP Authentication
     *
     * @return the JWT token if the request is successful else null.
     */
    private void performSRPAuthentication()
            throws SecurityException {

        InitiateAuthRequest initiateAuthRequest = initiateUserSrpAuthRequest();
        var cognitoIdp = CognitoIdentityProviderClient.builder().
                region(config.getRegion()).
                build();
        var initiateAuthResult = cognitoIdp.initiateAuth(initiateAuthRequest);
        if (ChallengeNameType.PASSWORD_VERIFIER.equals(
                initiateAuthResult.challengeName())) {
            var challengeRequest = userSrpAuthRequest(
                    initiateAuthResult, config.getPassword(),
                    initiateAuthRequest.authParameters().get("SECRET_HASH"));
            var result = cognitoIdp.respondToAuthChallenge(challengeRequest);
            token = result.authenticationResult().idToken();
            var expiresIn = result.authenticationResult().expiresIn()
                        - config.getTimeBeforeExpiryToRefresh();
            expiresAt = expiresIn * 1000 + currentTimeMillis();
        } else {
            throw new SecurityException("Challenge was not PASSWORD_VERIFIER");
        }
    }

    /**
     * Initialize the authentication request for the first time.
     *
     * @param username The user for which the authentication request is created.
     * @return the Authentication request.
     */
    private InitiateAuthRequest initiateUserSrpAuthRequest() {

        var pars = Map.of("USERNAME", config.getUsername(),
                      "SRP_A", getA().toString(16));
        if (config.hasSecretKey()) {
            pars.put("SECRET_HASH", calculateSecretHash());
        }
        return InitiateAuthRequest.builder().
                authFlow(AuthFlowType.USER_SRP_AUTH).
                authParameters(pars).
                clientId(config.getUserPoolClientId()).build();
    }

    /**
     * Method is used to respond to the Auth challenge from the user pool
     *
     * @param challenge The authenticaion challange returned from the cognito
     * user pool
     * @param password The password to be used to respond to the authentication
     * challenge.
     * @return the Request created for the previous authentication challenge.
     */
    private RespondToAuthChallengeRequest userSrpAuthRequest(
            InitiateAuthResponse challenge,
            String password, String secretHash
    ) {
        log.debug("Challenge: {}", challenge);
        log.debug("Config: {}", config);
        String userIdForSRP = challenge.challengeParameters().get("USER_ID_FOR_SRP");
        String usernameInternal = challenge.challengeParameters().get("USERNAME");

        BigInteger B = new BigInteger(challenge.challengeParameters().get("SRP_B"), 16);
        if (B.mod(AwsSRPAuthSupport.N).equals(BigInteger.ZERO)) {
            throw new SecurityException("SRP error, B cannot be zero");
        }

        BigInteger salt = new BigInteger(challenge.challengeParameters().get("SALT"), 16);
        byte[] key = getPasswordAuthenticationKey(userIdForSRP, password, B, salt);

        Date timestamp = new Date();
        byte[] hmac = null;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
                "EEE MMM d HH:mm:ss z yyyy", Locale.US);
        simpleDateFormat.setTimeZone(new SimpleTimeZone(
                SimpleTimeZone.UTC_TIME, "UTC"));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA256");
            mac.init(keySpec);
            mac.update(config.getUserPoolId().split("_", 2)[1].getBytes(UTF_8));
            mac.update(userIdForSRP.getBytes(UTF_8));
            byte[] secretBlock = Base64.getDecoder().decode(
                    challenge.challengeParameters().get("SECRET_BLOCK"));
            mac.update(secretBlock);
            String dateString = simpleDateFormat.format(timestamp);
            byte[] dateBytes = dateString.getBytes(UTF_8);
            hmac = mac.doFinal(dateBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error", e);
        }

        var srpAuthResponses = new HashMap(Map.of(
                "PASSWORD_CLAIM_SECRET_BLOCK",
                challenge.challengeParameters().get("SECRET_BLOCK"),
                "PASSWORD_CLAIM_SIGNATURE",
                Base64.getEncoder().encodeToString(hmac),
                "TIMESTAMP", simpleDateFormat.format(timestamp)));
        srpAuthResponses.put("USERNAME", usernameInternal);
        if (secretHash != null && !secretHash.isEmpty()) {
            srpAuthResponses.put("SECRET_HASH", secretHash);
        }

        return RespondToAuthChallengeRequest.builder().
                challengeName(challenge.challengeName()).
                clientId(config.getUserPoolClientId()).
                session(challenge.session()).
                challengeResponses(srpAuthResponses).build();
    }

    /**
     * Calculate the secret hash to be sent along with the authentication
     * request.
     *
     * @return Calculated secret hash.
     */
    private String calculateSecretHash() {

        final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

        SecretKeySpec signingKey = new SecretKeySpec(
                config.getSecretKey().getBytes(StandardCharsets.UTF_8),
                HMAC_SHA256_ALGORITHM);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            mac.init(signingKey);
            mac.update(config.getUsername().getBytes(StandardCharsets.UTF_8));
            byte[] rawHmac = mac.doFinal(config.getUserPoolClientId().
                    getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Error while calculating ");
        }
    }

    /**
     * Internal class for doing the Hkdf calculations.
     */
    final static class Hkdf {

        private static final int MAX_KEY_SIZE = 255;
        private final byte[] EMPTY_ARRAY = new byte[0];
        private final String algorithm;
        private SecretKey prk = null;

        /**
         * @param algorithm REQUIRED: The type of HMAC algorithm to be used.
         */
        private Hkdf(String algorithm) {
            if (!algorithm.startsWith("Hmac")) {
                throw new IllegalArgumentException("Invalid algorithm " + algorithm
                                                   + ". Hkdf may only be used with Hmac algorithms.");
            } else {
                this.algorithm = algorithm;
            }
        }

        private static Hkdf getInstance(String algorithm) throws NoSuchAlgorithmException {

            return new Hkdf(algorithm);
        }

        /**
         * @param ikm REQUIRED: The input key material.
         */
        public void init(byte[] ikm) {
            this.init(ikm, (byte[]) null);
        }

        /**
         * @param ikm REQUIRED: The input key material.
         * @param salt REQUIRED: Random bytes for salt.
         */
        private void init(byte[] ikm, byte[] salt) {
            byte[] realSalt = salt == null ? EMPTY_ARRAY : (byte[]) salt.clone();
            byte[] rawKeyMaterial = EMPTY_ARRAY;

            try {
                final Mac e = Mac.getInstance(this.algorithm);
                if (realSalt.length == 0) {
                    realSalt = new byte[e.getMacLength()];
                    Arrays.fill(realSalt, (byte) 0);
                }

                e.init(new SecretKeySpec(realSalt, this.algorithm));
                rawKeyMaterial = e.doFinal(ikm);
                final SecretKeySpec key = new SecretKeySpec(rawKeyMaterial, this.algorithm);
                Arrays.fill(rawKeyMaterial, (byte) 0);
                this.unsafeInitWithoutKeyExtraction(key);
            } catch (final GeneralSecurityException var10) {
                throw new RuntimeException("Unexpected exception", var10);
            } finally {
                Arrays.fill(rawKeyMaterial, (byte) 0);
            }

        }

        /**
         * @param rawKey REQUIRED: Current secret key.
         * @throws InvalidKeyException
         */
        private void unsafeInitWithoutKeyExtraction(SecretKey rawKey) throws InvalidKeyException {
            if (!rawKey.getAlgorithm().equals(this.algorithm)) {
                throw new InvalidKeyException(
                        "Algorithm for the provided key must match the algorithm for this Hkdf. Expected "
                        + this.algorithm + " but found " + rawKey.getAlgorithm());
            } else {
                this.prk = rawKey;
            }
        }

        /**
         * @param info REQUIRED
         * @param length REQUIRED
         * @return converted bytes.
         */
        private byte[] deriveKey(String info, int length) {
            return deriveKeyFromBytes(info != null ? info.getBytes(UTF_8) : null, length);
        }

        /**
         * @param info REQUIRED
         * @param length REQUIRED
         * @return converted bytes.
         */
        private byte[] deriveKeyFromBytes(byte[] info, int length) {
            final byte[] result = new byte[length];

            try {
                this.deriveKey(info, length, result, 0);
                return result;
            } catch (final ShortBufferException var5) {
                throw new RuntimeException(var5);
            }
        }

        /**
         * @param info REQUIRED
         * @param length REQUIRED
         * @param output REQUIRED
         * @param offset REQUIRED
         * @throws ShortBufferException
         */
        private void deriveKey(byte[] info, int length, byte[] output, int offset)
                throws ShortBufferException {
            this.assertInitialized();
            if (length < 0) {
                throw new IllegalArgumentException(
                        "Length must be a non-negative value.");
            } else if (output.length < offset + length) {
                throw new ShortBufferException();
            } else {
                final Mac mac = this.createMac();
                if (length > MAX_KEY_SIZE * mac.getMacLength()) {
                    throw new IllegalArgumentException(
                            "Requested keys may not be longer than 255 "
                            + "times the underlying HMAC length.");
                } else {
                    byte[] t = EMPTY_ARRAY;

                    try {
                        int loc = 0;

                        for (byte i = 1; loc < length; ++i) {
                            mac.update(t);
                            mac.update(info);
                            mac.update(i);
                            t = mac.doFinal();

                            for (int x = 0; x < t.length && loc < length; ++loc) {
                                output[loc] = t[x];
                                ++x;
                            }
                        }
                    } finally {
                        Arrays.fill(t, (byte) 0);
                    }

                }
            }
        }

        /**
         * @return the generates message authentication code.
         */
        private Mac createMac() {
            try {
                final Mac ex = Mac.getInstance(this.algorithm);
                ex.init(this.prk);
                return ex;
            } catch (final NoSuchAlgorithmException | InvalidKeyException var2) {
                throw new RuntimeException(var2);
            }
        }

        /**
         * Checks for a valid pseudo-random key.
         */
        private void assertInitialized() {
            if (this.prk == null) {
                throw new IllegalStateException("Hkdf has not been initialized");
            }
        }
    }
}
