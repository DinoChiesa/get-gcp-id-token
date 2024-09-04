// Copyright © 2024 Google LLC.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.google.examples.tokens;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class GetIdTokenWithServiceAccount {

  public static PrivateKey getPrivateKeyFromPem(String privateKeyString) throws Exception {

    if (privateKeyString.contains("-----BEGIN PRIVATE KEY-----")) {
      privateKeyString =
          privateKeyString
              .replace("-----BEGIN PRIVATE KEY-----\n", "")
              .replace("-----END PRIVATE KEY-----", "")
              .trim();
    }
    privateKeyString = privateKeyString.replaceAll("\n", "");

    KeyFactory kf = KeyFactory.getInstance("RSA");
    byte[] keyBytes = Base64.getDecoder().decode(privateKeyString);
    PKCS8EncodedKeySpec keySpecPv = new PKCS8EncodedKeySpec(keyBytes);
    PrivateKey privateKey = kf.generatePrivate(keySpecPv);
    return privateKey;
  }

  private static String base64UrlEncode(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String base64UrlEncode(String data) {
    return base64UrlEncode(data.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] getRSASignature(String message, String privateKeyString) throws Exception {
    PrivateKey privateKey = getPrivateKeyFromPem(privateKeyString);
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(privateKey);
    signature.update(message.getBytes(StandardCharsets.UTF_8));
    byte[] signatureBytes = signature.sign();
    return signatureBytes;
  }

  private static Map<String, String> readCredsFileAsMap(String credsfile) throws Exception {
    Gson gson = new Gson();
    Type t = new TypeToken<Map<String, String>>() {}.getType();
    Map<String, String> map;
    try (BufferedReader br = new BufferedReader(new FileReader(credsfile))) {
      map = gson.fromJson(br, t);
    }
    return map;
  }

  private static String getRSASignedJwt(
      final Map<String, String> credsfileMap, final String targetAudience) throws Exception {
    String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
    long iat = Instant.now().getEpochSecond();
    long exp = iat + 60;

    String private_key_string = credsfileMap.get("private_key");
    String sa_email = credsfileMap.get("client_email");
    String aud = credsfileMap.get("token_uri");
    String payload = "";
    payload += String.format("\"iss\":\"%s\",", sa_email);
    payload += String.format("\"aud\":\"%s\",", aud);
    payload += String.format("\"exp\":%d,", exp);
    payload += String.format("\"iat\":%d,", iat);
    payload += String.format("\"target_audience\":\"%s\"", targetAudience);
    payload = "{" + payload + "}";

    String prettyPayload =
        payload.replace("{", "{\n  ").replace("}", "\n}").replaceAll(",", ",\n  ");
    System.out.printf("jwt payload: %s\n\n", prettyPayload);
    String to_be_signed = String.format("%s.%s", base64UrlEncode(header), base64UrlEncode(payload));
    byte[] signature = getRSASignature(to_be_signed, private_key_string);
    return String.format("%s.%s", to_be_signed, base64UrlEncode(signature));
  }

  private static String getFormDataAsString(Map<String, String> formData) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> singleEntry : formData.entrySet()) {
      if (sb.length() > 0) {
        sb.append("&");
      }
      sb.append(URLEncoder.encode(singleEntry.getKey(), StandardCharsets.UTF_8))
          .append("=")
          .append(URLEncoder.encode(singleEntry.getValue(), StandardCharsets.UTF_8));
    }
    return sb.toString();
  }

  private static String httpPostToken(final String uri, String assertion)
      throws URISyntaxException, IOException, InterruptedException {
    Map<String, String> formData = new HashMap<String, String>();
    formData.put("assertion", assertion);
    formData.put("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI(uri))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(getFormDataAsString(formData)))
            .build();
    HttpClient client = HttpClient.newHttpClient();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    HttpHeaders headers = response.headers();
    String body = response.body();
    return body;
  }

  private static void showTokenInfo(String token)
      throws URISyntaxException, IOException, InterruptedException {
    final String uri =
        String.format("https://www.googleapis.com/oauth2/v3/tokeninfo?id_token=%s", token);
    HttpRequest request = HttpRequest.newBuilder().uri(new URI(uri)).GET().build();
    HttpClient client = HttpClient.newHttpClient();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    HttpHeaders headers = response.headers();
    String body = response.body();
    System.out.printf("\ntoken info:\n%s\n", body);
  }

  private static String getToken(String credsfile, String targetAudience) throws Exception {
    Map<String, String> credsfileMap = readCredsFileAsMap(credsfile);
    String signedJwt = getRSASignedJwt(credsfileMap, targetAudience);
    String body = httpPostToken(credsfileMap.get("token_uri"), signedJwt);

    Gson gson = new Gson();
    Type t = new TypeToken<Map<String, Object>>() {}.getType();

    try (BufferedReader br = new BufferedReader(new StringReader(body))) {
      Map<String, Object> map = gson.fromJson(br, t);
      return (String) map.get("id_token");
    }
  }

  public static void usage() {
    System.out.println("GetIdTokenWithServiceAccount: get a GCP ID Token using Java.\n");
    System.out.println(
        "Usage:\n"
            + "  java com.google.examples.tokens.GetIdTokenWithServiceAccount --creds"
            + " <json-key-file> --audience AUDIENCE [--inquire]");
  }

  public static class BadParamsException extends IllegalArgumentException {
    public BadParamsException() {
      super();
    }

    public BadParamsException(String message) {
      super(message);
    }

    public BadParamsException(String message, Throwable cause) {
      super(message, cause);
    }

    public BadParamsException(Throwable cause) {
      super(cause);
    }
  }

  public static void main(String[] args) {
    try {
      final int L = args.length;
      if (L < 2) {
        throw new BadParamsException("incorrect arguments");
      }
      String audience = null;
      String credsfile = null;
      boolean wantInquire = false;
      for (int i = 0; i < L; i++) {
        String arg = args[i];
        switch (arg) {
          case "--audience":
            audience = args[++i];
            break;
          case "--inquire":
            wantInquire = true;
            break;
          case "--creds":
            credsfile = args[++i];
            break;
          default:
            throw new BadParamsException(String.format("unhandled argument: %s", arg));
        }
      }

      if (audience == null) {
        throw new BadParamsException("missing required argument: --audience");
      }
      if (credsfile == null) {
        throw new BadParamsException("missing required argument: --creds");
      }
      String token = getToken(credsfile, audience);
      System.out.printf("id token: %s\n", token);

      if (wantInquire) {
        showTokenInfo(token);
      }
    } catch (BadParamsException bpe) {
      System.out.println("Exception:" + bpe.getMessage());
      usage();
    } catch (Exception exc1) {
      System.out.println("Exception:" + exc1.toString());
      exc1.printStackTrace();
    }
  }
}
