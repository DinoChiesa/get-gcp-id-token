# Python example to get ID token

Prerequisites:
- Python 3.10 or later


Install:
```sh
python -m venv .venv
source .venv/bin/activate
pip install "PyJWT[crypto]" requests
```

Run the command:

```sh
python ./getIdTokenWithServiceAccount.py  --audience  https://foo-bar/bam \
   --keyfile path-to-my/service-account-6f0f7bfd9658.json
```

The output will show:

```
JWT Payload:
{
  "iss": "open-policy-agent@dchiesa-argolis-2.iam.gserviceaccount.com",
  "aud": "https://oauth2.googleapis.com/token",
  "iat": 1749240371,
  "exp": 1749240431,
  "target_audience": "https://foo-bar/bam"
}

Signed Assertion:
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJvcGVuLXBvbGljeS1hZ2VudEBkY2hpZXNhLWFyZ29saXMtMi5pYW0uZ3NlcnZpY2VhY2NvdW50LmNvbSIsImF1ZCI6Imh0dHBzOi8vb2F1dGgyLmdvb2dsZWFwaXMuY29tL3Rva2VuIiwiaWF0IjoxNzQ5MjQwMzcxLCJleHAiOjE3NDkyNDA0MzEsInRhcmdldF9hdWRpZW5jZSI6Imh0dHBzOi8vZm9vLWJhci9iYW0ifQ.LrhcT1dcb_tPNgAijGYuQUMYfBvMfm4JcqerZzIYOZOT3-Gr-I2uWyeMew0v7aymE_3Y7O3wLJYeczBzzFfHvSjpLA4e-0SNyAHFEUCSrS04m-VFggoCPNVHNwcV7Na9THdidMHy1xhUuaIKwCCPdTj9BbvwltrDQ5hTkqY1bn2Alp0b-ugOTBLPRP1PtcCJn4yqXx_VjH5LKyVOsc3nLGWFBoFewX2NZoeeDH9Jqx4w6pT6lGugxvlZ4QD1wjCbSmMnwDvbck4eVjU_Gqv2AcRR5cTeyvprs_1V0KqoHNaF5xXHar-TqmmU5SATKbXBXfgGjwkV3oiEC5vduXnl3A

Token Response:
{
  "id_token":
  "eyJhbGciOiJSUzI1NiIsImtpZCI6ImJiNDM0Njk1OTQ0NTE4MjAxNDhiMzM5YzU4OGFlZGUzMDUxMDM5MTkiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2Zvby1iYXIvYmFtIiwiYXpwIjoib3Blbi1wb2xpY3ktYWdlbnRAZGNoaWVzYS1hcmdvbGlzLTIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6Im9wZW4tcG9saWN5LWFnZW50QGRjaGllc2EtYXJnb2xpcy0yLmlhbS5nc2VydmljZWFjY291bnQuY29tIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsImV4cCI6MTc0OTI0Mzk3MSwiaWF0IjoxNzQ5MjQwMzcxLCJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJzdWIiOiIxMDE4NzI5OTYwOTA1NTIwMDczMjMifQ.eYyJ4Mz9bKpkqPUP_BnIoc5BtemfkexA2d6CdR8xkrAeK4NzOw_ZXuN7MJ6f0x31gbCLEwSdIMmYHaEpVtnZrmEvX7DUJtbaEwXd2DJuw_Wwcj4nt8GwxjasLayl4hI7fb99EotlKYctPYjCQhWgJJuibHenx4dkAC0RcwvyH0rGmnDU8897iJXnKkHjY5apBqpZjh6zWbb4aaoz1wFOykmsk8RFEfuq9odG-L7qHCNULlps-mxXdUkDxkO7SDoowWSq0C9AhclEOI8IzaYB3XVtDiexc4QqA0o1Q2Y49bWWV0_RLs6o8Lx_sLtTwYsDcbuxXPYkOrLDL53if62bcA"
 
}

Token Info:
{
  "aud": "https://foo-bar/bam",
  "azp": "open-policy-agent@dchiesa-argolis-2.iam.gserviceaccount.com",
  "email": "open-policy-agent@dchiesa-argolis-2.iam.gserviceaccount.com",
  "email_verified": "true",
  "exp": "1749243971",
  "iat": "1749240371",
  "iss": "https://accounts.google.com",
  "sub": "101872996090552007323",
  "alg": "RS256",
  "kid": "bb43469594451820148b339c588aede305103919",
  "typ": "JWT"
}
```
