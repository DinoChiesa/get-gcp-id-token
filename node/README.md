# Nodejs example to get ID token

Prerequisites:
- npm v10.x or later
- nodejs v20.x or later


Install:
```sh
npm install
```

Run the command:

```sh
$ node ./getIdTokenWithServiceAccount.js --keyfile ./my-service-account-key-a8ef19f432a9.json --audience https://foo-bar/bam
```

The output will show:

```
jwt payload: {
  "iss": "sheet-writer-1@dchiesa-argolis-2.iam.gserviceaccount.com",
  "aud": "https://oauth2.googleapis.com/token",
  "iat": 1724976008,
  "exp": 1724976068,
  "target_audience": "https://foo-bar/bam"
}

assertion: eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzaGVldC13cml0ZXItMUBkY2hpZXNhLWFyZ29saXMtMi5pYW0uZ3NlcnZpY2VhY2NvdW50LmNvbSIsImF1ZCI6Imh0dHBzOi8vb2F1dGgyLmdvb2dsZWFwaXMuY29tL3Rva2VuIiwiaWF0IjoxNzI0OTc2MDA4LCJleHAiOjE3MjQ5NzYwNjgsInRhcmdldF9hdWRpZW5jZSI6Imh0dHBzOi8vZm9vLWJhci9iYW0ifQ.rxVh1-rpXxn67zh94LBDLJm3j2jDqzlXXxV9AqUVtVYDVoKvLy5PH7oBFxrO9RgnhvYkxmbYhMWC5bKmAsaB1J7Y7m3Ch7N2C05kzvle8RHImMsIdW7_nLEISKYgZLmUTQh_oqqgyysmY6C6q0Hadt7yqJ7rZz1W_-wq2fV0hZVTAZLKlUtXefKrwK90Myzo3yZg5tA7GTFUY23b8D4gSEkMxjGR0Ke3PwR4N9SK4FKy8YlYeDsOUGfX2GNmqEIQpD7AfjLBUbnFJeKVL04c8PKXnvUAiffeqfCkwcVpVNoyFexEsB2e9ZUUL2H4A7tRR4cA0DU3OL0dkm3Bhd9qHQ

token response:
{
  "id_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6ImE0OTM5MWJmNTJiNThjMWQ1NjAyNTVjMmYyYTA0ZTU5ZTIyYTdiNjUiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2Zvby1iYXIvYmFtIiwiYXpwIjoic2hlZXQtd3JpdGVyLTFAZGNoaWVzYS1hcmdvbGlzLTIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6InNoZWV0LXdyaXRlci0xQGRjaGllc2EtYXJnb2xpcy0yLmlhbS5nc2VydmljZWFjY291bnQuY29tIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsImV4cCI6MTcyNDk3OTYwOCwiaWF0IjoxNzI0OTc2MDA4LCJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJzdWIiOiIxMDk0MjUzOTg3OTkzMDczMjU0NjIifQ.e4yythPb-imAsnsYDUxhrAX_aqDrBSQ1mtkyREEVtsWqpce7UlgEIbfSFS-LQUNVJAp89_HNY4f9NjLmB4mtax335TXipWEjc4ofxn0JPFGU8DwPjSWeb_39JQzLzMevAqzdYxK6tvGRmLU-D6S6NkbtWDn2Wun1ZKTh6RlzAFnPntx0AKtcECcb6vTvBCbVvgoIaJoFcA8CGIKZOr_dCqHsXTlHjggwCkLivyMfQPeVm7dI9EKgw0qFf9IPG0yyVlCfB3T4JSNRuphiifH9nDK1AF1txmjptAOkzFaR7tTwFesvzN3Y54OE0Y4TiI2jVni3xxxW0-Xe9loeIMgXrA"
}

token info:
{
  "aud": "https://foo-bar/bam",
  "azp": "sheet-writer-1@dchiesa-argolis-2.iam.gserviceaccount.com",
  "email": "sheet-writer-1@dchiesa-argolis-2.iam.gserviceaccount.com",
  "email_verified": "true",
  "exp": "1724979608",
  "iat": "1724976008",
  "iss": "https://accounts.google.com",
  "sub": "109425398799307325462",
  "alg": "RS256",
  "kid": "a49391bf52b58c1d560255c2f2a04e59e22a7b65",
  "typ": "JWT"
}
```
