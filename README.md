# Examples: How to Get a GCP ID Token

This repo contains code examples that illustrate how to get a GCP ID token programmatically,
sometimes using a Service Account key file, and sometimes not.

Within Google Cloud, services you host in Cloud Run, or functions you host in Cloud Functions, or any service protected behind Identity Aware Proxy (IAP), rely on ID tokens for authentication. (These scenarios are summarized [here](https://cloud.google.com/docs/authentication/get-id-token))

Things that have api endpoints like _something_.googleapis.com require an _Access Token_, not an ID token.

One good way to think about it, if you're focused on Cloud Run: If you want to administer Cloud Run, then you need an access token. If you want to invoke a Cloud Run service, then you need an ID Token.

## What does an ID token look like?

GCP issues ID tokens are JWT signed with RSA. The signed payload if an ID token for a service account looks like this:

```json
{
  "aud": "http://audience-varies.foo.bar",
  "azp": "name-of-service-account-1@my-gcp-project.iam.gserviceaccount.com",
  "email": "name-of-service-account-1@my-gcp-project.iam.gserviceaccount.com",
  "email_verified": true,
  "exp": 1725484881,
  "iat": 1725481281,
  "iss": "https://accounts.google.com",
  "sub": "109425398799307325462"
}
```

## What are ID tokens good for?

If you have a Cloud Run service and it is not configured to [allow unauthenticated
access](https://cloud.google.com/run/docs/authenticating/public), then an ID token
is required to invoke it.

This is true whether or not the Cloud Run service is protected by IAP.  A request
must pass in the ID token in the Authorization header of an HTTP request. Using
curl, that would look like so:

```sh
curl -i -H "Authorization: Bearer $ID_TOKEN" https://SERVICE_NAME-PROJECT_ID.REGION.run.app/
```

If you interactively visit an IAP-protected Cloud Run endpoint within a browser window, IAP
will automatically redirect you to login interactively, and then bring you back
to the Cloud Run endpoint. That part is easy.

If you interactively visit a Cloud Run endpoint within a browser window which is
not protected by IAP, then your Cloud Run service needs to handle the login
experience.

But if you want a headless system outside of Google Cloud to invoke a Cloud Run
system, that system must obtain an ID token, and it will not "interactively
login"!  So your calling system needs to pro-actively obtain its own ID token.
This repo will explore that a little later. Before we get to that, let's cover a
little more background.

## Decoding tokens

Decode the ID token, as you would decode any signed JWT. Split by dots, then
base64-decode the first two sections to decode it.

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
administrative requests. But other systems, notably Cloud Run and Cloud Functions,
if they are configured to NOT allow unauthenticated access, require [_ID
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

OK that is all I will say in this repo about Access Tokens. The rest of this document will
talk about ID tokens.


## Ways to Get an ID Token

From here on out, I'm going to be talking about getting an ID token on behalf of a _service account_.

| approach                                                                                                 | recommended for...                                                                                                                  |
|----------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| via the gcloud command line tool                                                                         | works for an individual user (like you, dear reader) and also for a service account. But primarily\* in an interactive environment. |
| via the IAM credentials service (iamcredentials.googleapis.com)                                          | Works for any service that can invoke the endpoint. iamcredentials is available to systems outside of Google Cloud.                 |
| via a REST "shortcut", using the metadata endpoint for Google Compute Engine. (metadata.google.internal) | For workloads running on GCE, including but not limited apps running directly in GCE VMs, Cloud Run,  or Cloud Functions.           |
| using a service-account key and a special OAuthV2 grant, invoking oauth2.googleapis.com                    | Recommended only when none of the above apply.                                                                                           |


There are various libraries and frameworks available for different programming
languages, but basically they all are wrappers on these token acquisition methods.

Below I'll describe some of these approaches in more detail.


## The gcloud command line utility

If you haven't met [the `gcloud`
command](https://cloud.google.com/sdk/docs/install), you need to.  It's super
useful if you use anything in Google Cloud. To get an ID token for YOURSELF
using the gcloud command, run this:

```sh
gcloud auth print-identity-token
```

This ID token identifies YOU.

If you want to use `gcloud` to get an ID token on behalf of _a service account_,
a token you could eventually use when invoking a Cloud Run service, then you
need to use _this_ gcloud command:

```sh
project_id="id-of-my-gcp-project"
sa_email="my-account-1@${project_id}.iam.gserviceaccount.com"
gcloud auth print-identity-token \
   --impersonate-service-account="${sa_email}" \
   --audiences="https://service-hash-uc.a.run.app"
```

To make the above possible, you must have the impersonation rights on that
service account. The role required on the service account to grant that
permission is `roles/iam.serviceAccountTokenCreator`.  The following command shows how
you would grant yourself the correct permissions. This will work only if you
have `roles/iam.serviceAccountAdmin` role in the project, in other words if you
have the ability to adjust permissions on service accounts.

```
gwhoami=$(gcloud auth list --filter=status:ACTIVE --format="value(account)")
project_id="id-of-my-gcp-project"
gcloud iam service-accounts add-iam-policy-binding ${sa_email} \
  --member user:${gwhoami} \
  --role "roles/iam.serviceAccountTokenCreator"
  --project "${project_id}"
```

If you do not have the `roles/iam.serviceAccountAdmin` role, then you need to
find someone who has Editor or Owner role in your GCP project to grant to you,
the "serviceAccountUser" role on that particular service account.

\*Above, I said that this method is primarily intended for use in an interactive
environment.  I'm talking about a terminal or shell.  But that's not a strict
_requirement_.  Your nodejs script could use client_process to invoke that
command.  Or your Java program or Python program could do something similar. But
it would require that the `gcloud` tool be installed where the app is running,
and that the app is running in a shell with an identity that can impersonate the
desired service account. That might make sense during development but it doesn't
make sense for production-deployed apps or systems.

## Via the IAM Credentials endpoint

If you have an access token, and the access token is for a principal that has
rights to impersonate a service account, you can use the access token to get an
ID token for that service account.

This is documented [here](https://cloud.google.com/docs/authentication/get-id-token#impersonation).

You do not need to create or download or reference or manage a service account
_key file_ for this to work. To use this approach, just invoke the IAM credentials endpoint,
passing your access token.  By doing this, you're asking the IAM service inside
Google Cloud to issue a token for a service account that you have permissions to
impersonate.

```sh
ACCESS_TOKEN=$(gcloud auth print-access-token)
curl -i -X POST -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "content-type:application/json" -d '{"audience": "<SOME_URL>"}' \
  "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/${sa_email}:generateIdToken"
```

Here again, this requires that the principal that obtained the access token
(probably you, if you used `gcloud auth print-access-token`) has the role
`iam.serviceAccountTokenCreator` for the particular Service Account.  See the notes
above for more on that.

And yes, this also applies to the case where an app has an access token for a
Service Account, and the app wants to get an ID token for the same Service
Account.

In this case, you must grant the service account, the
"iam.serviceAccountTokenCreator" role _on itself_.  And you can then pass a
service account access token to that iamcredentials endpoint; a service account
can thus get an ID token for itself.


## The Metadata endpoint

This way is simpler: send a GET request to an endpoint and get an ID token
back. This is documented [here](https://cloud.google.com/docs/authentication/get-id-token#metadata-server).

It looks like this:

```sh
query="audience=${CLOUD_RUN_ENDPOINT}"
curl -X GET -H "Metadata-Flavor: Google" \
  "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity?${query}""
```

But the catch is, this works only if the command is run from a Google
Compute Engine (GCE) instance. It can be from a raw VM, or a Cloud Run app,
... or an Apigee API proxy!

> If you invoke that curl command from a Cloud Shell instance, you will get an
> ID token identifying _you_.

This request gets an ID token for the service account which is used by the
underlying GCE instance. Every VM in Google Cloud has a service account
identity; it will use "the default" SA identity, or one you specify.  This call
relies on that inherent identity, and it won't work if you try invoking that
endpoint from your laptop, or a build server that runs outside
of GCP.

As above, you do not need to create or download or reference or manage a service account
_key file_ for this to work.


## Using a key file, and the special `jwt-bearer` grant

You can get an ID token on behalf of a service account if you possess the service account key file.
The use of downloaded [Service Account credentials](https://cloud.google.com/iam/docs/service-account-creds) is discouraged by Google, and it is not subtle.

> Caution: Service account keys are a security risk if not managed correctly. You should choose a more secure alternative to service account keys whenever possible. If you must authenticate with a service account key, you are responsible for the security of the private key and for other operations described by Best practices for managing service account keys. ...

A main landing page describing the user of service account keys bears the title ["Migrate from service account keys"](https://cloud.google.com/iam/docs/migrate-from-service-account-keys), and the text there reads:

> Service account keys are commonly used to authenticate to Google Cloud services. However, they can also become a security risk if they're not managed properly, increasing your vulnerability to threats like credential leakage, privilege escalation, information disclosure, and non-repudiation. In many cases, you can authenticate with more secure alternatives to service account keys.

But it is still possible.
To use this approach, you must send a specially-formed self-signed JWT, to the /token endpoint.

This process is documented [here](https://cloud.google.com/iap/docs/authentication-howto#authenticate-service-account).

In more detail, the request-for-token looks like this:

```
POST https://oauth2.googleapis.com/token
Accept: application/json
content-type: application/x-www-form-urlencoded

assertion=ASSERTION&grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer
```

The ASSERTION must be a JWT signed with the private key belonging to the service
account. The payload of the JWT must look like the following:

```json
{
  "iss": "sheet-writer-1@dchiesa-argolis-2.iam.gserviceaccount.com",
  "aud": "https://oauth2.googleapis.com/token",
  "iat": 1724976008,
  "exp": 1724976068,
  "target_audience": "https://foo-bar/bam"
}
```

The expiry claim must be present and must be no more than 5 minutes after the issued-at time.

The code in this repo shows you how to do that, in a bash script, in python, in nodejs, and in Java.
All of them generate an ID token for a specific audience, using a service account key.

| language | link                                                                                      | comments                                                 |
|----------|-------------------------------------------------------------------------------------------|----------------------------------------------------------|
| bash     | [link](./bash/get-id-token-for-service-account.sh)                                        | relies on openssl, sed, curl, etc.                       |
| python   | [link](./py/getIdTokenWithServiceAccount.py)                                              | uses `"PyJWT[crypto]"`,  `requests`                      |
| nodejs   | [link](./node/getIdTokenWithServiceAccount.js)                                            | relies only on builtin modules.                          |
| java     | [link](./java/src/main/java/com.google.examples.tokens/GetIdTokenWithServiceAccount.java) | dependencies: Java11, gson                               |
| C#       | [link](./dotnet/GetGcpIdTokenForServiceAccount.cs)                                        | depends on: .NET 8.0 and System.IdentityModel.Tokens.Jwt |

> * Note: using any of the service account samples requires a service account key file in JSON format, containing the private key of the service account. In case you missed the warnings above, I'll repeat that [Google recommends against](https://cloud.google.com/docs/authentication#auth-decision-tree) creating and downloading service account keys, if you can avoid it.

## Pre-requisite for any of the following examples: create your service account key

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

9. download the JSON file to your local workstation. The contents will look something like this:
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

You could do all of this with gcloud, too.


## (bash) get-id-token-for-service-account.sh

This shows getting a token for a service account, in this
case, from a bash script.

The pre-requisities here are:
* bash (obviously)
* curl
* base64
* date, sed, tr
* openssl

There's no additional setup for the bash script. When you need a new access token, run the script:

```sh
cd bash
./get-id-token-for-service-account.sh ~/Downloads/my-service-account-key.json MY_AUDIENCE_HERE
```

## (python) getIdTokenWithServiceAccount

This shows getting a token for a service account, from a python script.

The pre-requisities here are:
* python >=3.10

You need a service account key json file; follow the steps described above.

To create a token, invoke the node script specifying the downloaded key file:

```sh
cd py/
# one-time install
python -m venv .venv
source .venv/bin/activate
pip install "PyJWT[crypto]" requests

# Subsequently
source .venv/bin/activate
python ./getIdTokenWithServiceAccount.py --audience https://foo-bar/bam \
   --keyfile path-to-my/service-account-6f0f7bfd9658.json
```

The result will be a JSON response shaped something like this:

```json
{
  "id_token": "eyJhbGciOiJSUzI..."
}
```

## (nodejs) getIdTokenWithServiceAccount

This shows getting a token for a service account, from a nodejs script.

You need a service account key json file. See the instructions above.

The pre-requisities here are:
* [node >=20](https://nodejs.org/en/download)
* npm

To create a token, invoke the node script specifying the downloaded key file

```sh
cd node/
npm install
node ./getIdTokenWithServiceAccount.js -v  --keyfile ~/Downloads/my-service-account-key.json --audience FOO
```

The result will be a JSON response shaped something like this:

```json
{
  "id_token": "eyJhbGciOiJSUzI..."
}
```


## (java) GetIdTokenWithServiceAccount.java

The pre-requisite here is a JDK v11 or later. And you need Apache maven v3.9 or later.

As above, you need a service account key json file.

Then, build and run the app. Follow these steps. I tested this on Debian Linux and MacOS.

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
   java -jar ./target/get-gcp-id-token-20250606.jar --creds YOUR_KEY_FILE.json --audience FOO_BAR
   ```

   The result should be an ID token.


   You can also tell the program to send the token to the tokeninfo endpoint:
   ```
   java -jar ./target/get-gcp-id-token-20250606.jar --creds YOUR_KEY_FILE.json --audience FOO_BAR --inquire
   ```

   ...and you should see the token info output.


## (C#) GetGcpIdTokenForServiceAccount.cs

Pre-requisites:
- [.NET 8.0](https://dotnet.microsoft.com/en-us/download/dotnet/8.0)

Dependencies:
- [System.IdentityModel.Tokens.Jwt](https://www.nuget.org/packages/system.identitymodel.tokens.jwt/)

As above, you need a service account key json file.

Then, build and run the app. Follow these steps. I tested this on Linux/Debian

1. verify your dotnet version
   ```
   cd dotnet
   dotnet --version
   ```

   You should see v8.0.301 or later

2. build
   ```
   dotnet add package System.IdentityModel.Tokens.Jwt
   dotnet build
   ```

   This should show you some happy messages.

3. run
   ```
   dotnet run Get-GCP-ID-Token.dll --keyfile YOUR_KEY_FILE.json  --audience DESIRED_AUDIENCE
   ```

   The result should be an ID token.
   ...and you should see the token info output.


## Disclaimer

This example is not an official Google product, nor is it part of an
official Google product.

## License

This material is [Copyright 2021-2025 Google LLC](./NOTICE).
and is licensed under the [Apache 2.0 License](LICENSE).


## Support

The examples here are open-source software.
If you need assistance, you can try inquiring on [Google Cloud Community
forum dedicated to Apigee](https://www.googlecloudcommunity.com/gc/Apigee/bd-p/cloud-apigee).
There is no service-level guarantee for
responses to inquiries regarding this example.
