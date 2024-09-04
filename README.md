# Examples: How to Get a GCP ID Token

This repo contains code examples that illustrate how to get a GCP ID token programmatically,
specifically from a Service Account key file.

Within Google Cloud, services you host in Cloud Run, or functions you host in Cloud Functions, and then protect with Identity Aware Proxy (IAP), rely on ID tokens for authentication.

> This is distinct from things that have api endpoints like _something_.googleapis.com; those systems require an Access Token, not an ID token.

If you want to administer Cloud Run, then you need an access token. If you want to invoke a Cloud Run service, then you need an ID Token.

## What does an ID token look like?

The form of a GCP-issued ID token is an RSA-signed JWT. The signed payload looks like this:
```json
{
  "aud": "http://goo.bar",
  "azp": "sheet-writer-1@dchiesa-argolis-2.iam.gserviceaccount.com",
  "email": "sheet-writer-1@dchiesa-argolis-2.iam.gserviceaccount.com",
  "email_verified": true,
  "exp": 1725484881,
  "iat": 1725481281,
  "iss": "https://accounts.google.com",
  "sub": "109425398799307325462"
}
```

## What are ID tokens good for?

An ID token is required to invoke Cloud Run, if it is protected by IAP.  A
request must pass in the ID token in the Authorization header of an HTTP
request. Using curl, that would look like so:

```sh
curl -i -H "Authorization: Bearer $ID_TOKEN" https://SERVICE_NAME-PROJECT_ID.REGION.run.app/
```

If you interactively visit a Cloud Run endpoint within a browser window, IAP
will automatically redirect you to login interactively, and then bring you back
to the Cloud Run endpoint.  That part is easy.

But if you want a system outside of Google Cloud to invoke a Cloud Run system,
that system must obtain an ID token, and it cannot "interactively login"!


## Decoding tokens

Decode the ID token, as you would decode any signed JWT. Split by dots, then base64-decode the first two sections to decode it.

Or use an online tool [like this one](https://dinochiesa.github.io/jwt/) to decode your ID Token.

You  can also send the ID token to the googleapis tokeninfo endpoint to ask Google
to tell you about it. Like so:

```
curl -i https://www.googleapis.com/oauth2/v3/tokeninfo\?id_token=$ID_TOKEN
```

It will look something like this:
```
{
  "aud": "http://goo.bar",
  "azp": "sheet-writer-1@dchiesa-argolis-2.iam.gserviceaccount.com",
  "email": "sheet-writer-1@dchiesa-argolis-2.iam.gserviceaccount.com",
  "email_verified": "true",
  "exp": "1725484881",
  "iat": "1725481281",
  "iss": "https://accounts.google.com",
  "sub": "109425398799307325462",
  "alg": "RS256",
  "kid": "b2f80c634600ed13072101a8b4220443433db282",
  "typ": "JWT"
}
```

## Access Tokens vs ID Tokens

For any principal, it is also possible to get a different kind of token - an Access
Token, also known as an identity token.  You can get an Access token for a user, or
for a service account.

Google APIs (*.googleapis.com) accept _access tokens_ when authorizing
administrative requests. But other systems, notably Cloud Run and Cloud Functions, if they are configured to NOT allow unauthenticated access, require [_ID
tokens_](https://cloud.google.com/docs/authentication/get-id-token) as the
credential.

And the principal identified by that token must have the appropriate
permissions on the service or function. In the case of Cloud Run, it's
`roles/run.invoker`; in the case of Cloud Functions, it's
`roles/cloudfunctions.invoker`.

In general the pattern is:

- If it's a builtin Google Cloud service, like PubSub, or FhirStore, or Cloud
  Logging, etc etc etc, basically any endpoint hosted at *.googleapis.com ,
  then you need to use an Access Token.

- If it's code that you've written and published, like with Cloud Run Services
  and Cloud Functions, then if you've enabled "Authorization", you should use an
  ID Token.


OK that is all I will say in this repo  about Access Tokens. The rest of this document will
talk about ID tokens.


## Ways to Get an ID Token

1. via the gcloud command line tool
2. via the IAM credentials service
3. via a REST "shortcut", using the metadata endpoint for Google Compute Engine.
2. via a service-account key and a special OAuthV2 grant

There are various libraries and frameworks , but basically they all are wrappers on the token dispensing APIs.

## The gcloud command line utility

If you haven't met [the `gcloud` command)[https://cloud.google.com/sdk/docs/install], you need to.  It's super useful if you use anything in Google Cloud. To get an ID token for YOURSELF using the gcloud command, run this:

```sh
gcloud auth print-identity-token
```

This ID token identifies YOU.

If you want to get an ID token on behalf of a service account for use with a Cloud Run service, then you need to specify different options:

```sh
gcloud auth print-identity-token \
   --impersonate-service-account="my-account-1@project-name.iam.gserviceaccount.com" \
   --audiences="https://service-hash-uc.a.run.app"
```


## Via the IAM Credentials endpoint

Invoke the IAM credentials endpoint. You're asking the IAM service inside
Google Cloud to issue a token for a service account that you have permissions to impersonate.

```sh
ACCESS_TOKEN=$(gcloud auth print-access-token)
curl -i -X POST -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "content-type:application/json" -d '{"audience": "<SOME_URL>"}' \
  "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/${sa_email}:generateIdToken"
```


## The Metadata endpoint

This way is the simplest: send a GET request to an endpoint and get an ID token
back. Like this:

```sh
query="audience=${CLOUD_RUN_ENDPOINT}"
curl -X GET -H "Metadata-Flavor": "Google" \
  "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity?${query}""
```

But the catch is, this works if and only if the command is run from a Google
Compute Engine (GCE) instance. It can be from a raw VM, or a Cloud Run app, or a
Cloud Shell instance... or an Apigee API!  This request gets an ID token for the
service account which is used by the GCE instance. You do not need to create or
download or reference a service account _key file_ for this to work. This call
won't work if you try invoking that endpoint from your laptop, or a build server
that runs outside of GCP.


## Via the special `jwt-bearer` grant 

You can get an ID token on behalf of a service account if you possess the service account key file. 
To do so, you must send a specially-formed signed JWT, to the /token endpoint. 

In more detail, the request-for-token looks like this: 

```
POST https://oauth2.googleapis.com/token
Accept: application/json
content-type: application/x-www-form-urlencoded

assertion=ASSERTION&grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer
```

The ASSERTION must be a JWT signed with the private key belonging to the service account. The payload of the JWT must look like the following: 
```json
{
  "iss": "sheet-writer-1@dchiesa-argolis-2.iam.gserviceaccount.com",
  "aud": "https://oauth2.googleapis.com/token",
  "iat": 1724976008,
  "exp": 1724976068,
  "target_audience": "https://foo-bar/bam"
}
```

The  expiry claim must be present and must be no more than 5 minutes after the issued-at time. 

The code in this repo shows you how to do that, in shell script, nodejs and in Java. 

There are currently these examples here:

* [**get-id-token-for-service-account.sh**](./sh/get-id-token-for-service-account.sh) - a bash script that gets
  an ID token using a service account key (*see note below).

* [**getIdTokenWithServiceAccount.js**](./node/getIdTokenWithServiceAccount.js) - a [nodejs](https://nodejs.org/en/)
  script that gets an ID token for a specific audience, using a service
  account key. (*see note below).

* [**GetIdTokenWithServiceAccount.java**](./java/src/main/java/com.google.examples.tokens/GetIdTokenWithServiceAccount.java) - a
  java program that gets an ID token for a specific audience, using a service account key. (*see note
  below).

> * Note: using any of the service account samples requires a service account key
  file in JSON format, containing the private key of the service account. Be aware that [Google recommends against](https://cloud.google.com/docs/authentication#auth-decision-tree) creating and downloading service account keys, if you can avoid it.


## (bash) get-id-token-for-service-account.sh

This shows getting a token for a service account, in this
case, from a bash script.

The pre-requisities here are:
* curl
* base64
* date, sed, tr
* openssl

To set up, you need a service account JSON file containing the private key of
the service account.

Follow these steps for the one-time setup:

1. visit console.cloud.google.com

2. select your desired "project".  Service accounts are maintained within the scope of a GCP project.

3. Using the left-hand-side, Navigate to "IAM & Admin".

4. Again using the LHS nav, Click "Service Accounts"

5. Create a new service account, or select a pre-existing one to use.

6. Once created, select the service account

7. In the "Service account details" panel, select the KEYS tab

8. Add a new Key, create new key

9. select JSON

9. Create

9. download the JSON file to your local workstation. The result is something like this:
   ```json
   {
     "type": "service_account",
     "project_id": "projectname1",
     "private_key_id": "93158289b2734d823aaeba3b1e4a48a15aaac",
     "client_email": "service_acct_name@projectname1.iam.gserviceaccount.com",
     "client_id": "1167082158558367844",
     "auth_uri": "https://accounts.google.com/o/oauth2/auth",
     "token_uri": "https://oauth2.googleapis.com/token",
     "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
     "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/service_acct_name%40projectname1.iam.gserviceaccount.com",
     "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQE...8K5WjX\n-----END PRIVATE KEY-----\n"
   }
   ```

That thing is a secret. Protect it as such.

That is all one-time setup stuff.

Now, when you need a new access token, run the script:

```sh
cd sh
./get-id-token-for-service-account.sh ~/Downloads/my-service-account-key.json MY_AUDIENCE_HERE
```



## (nodejs) getIdTokenWithServiceAccount

This shows getting a token for a service account, from a nodejs script.

You need a service account key json file. To get it, follow the steps to
generate and download a json key file, as described for the bash example for
service accounts above. If you've already done it for the bash example, you do
not need to repeat that setup for this example.

Now, as often as you need to create a token, run these steps:

1. invoke the node script specifying the downloaded key file
   ```sh
   cd node/
   npm install
   node ./getIdTokenWithServiceAccount.js -v  --keyfile ~/Downloads/my-service-account-key.json --audience FOO
   ```

   The result will be a JSON response shaped something like this:

   ```json
   {
     "id_token": "ya29.c.b0AXv0zTPIXDh-FGN_hM4e....jN8H3fp50U............"
   }
   ```

## (java) GetIdTokenWithServiceAccount.java

The pre-requisite here is a JDK v11 or later. And you need Apache maven v3.9 or later

You need a service account key json file. To get it, follow the steps to
generate and download a json key file, as described for the bash example for
service accounts above. If you've already done it for the bash example, you do
not need to repeat that setup for this example.

Then, build and run the app. Follow these steps. I tested this on MacOS.

1. verify your java version
   ```
   cd java
   javac --version
   ```

   You should see v11.0.22 or later

2. and verify your version of maven
   ```
   mvn --version
   ```

   You should see `Apache Maven 3.9.0` or later

3. build
   ```
   mvn clean package
   ```

   This should show you some happy messages.

3. run
   ```
   java -jar ./target/get-gcp-id-token-1.0.1.jar --creds YOUR_KEY_FILE.json --audience FOO_BAR
   ```

   The result should be an ID token.


   You can also tell the program to send the token to the tokeninfo endpoint:
   ```
   java -jar ./target/get-gcp-access-token-1.0.1.jar --creds YOUR_KEY_FILE.json --audience FOO_BAR --inquire
   ```

   ...and you should see the token info output.


## Disclaimer

This example is not an official Google product, nor is it part of an
official Google product.

## License

This material is [Copyright 2021-2024 Google LLC](./NOTICE).
and is licensed under the [Apache 2.0 License](LICENSE).


## Support

The examples here are open-source software.
If you need assistance, you can try inquiring on [Google Cloud Community
forum dedicated to Apigee](https://www.googlecloudcommunity.com/gc/Apigee/bd-p/cloud-apigee).
There is no service-level guarantee for
responses to inquiries regarding this example.
