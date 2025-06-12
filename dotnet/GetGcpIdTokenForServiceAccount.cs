// Copyright © 2025 Google LLC.
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

using System;
using System.Collections.Generic;
using System.IdentityModel.Tokens.Jwt;
using System.IO;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading.Tasks;
using Microsoft.IdentityModel.Tokens;

public class GetIdTokenWithServiceAccount
{
    // A C# record to hold the parsed command-line arguments.
    private record Options(string KeyFile, string Audience);

    private static readonly JsonSerializerOptions jsonSerializerOptions = new JsonSerializerOptions
    {
        WriteIndented = true,
    };

    // A C# class to represent the structure of the Google Service Account key file.
    private class ServiceAccountKey
    {
        [JsonPropertyName("token_uri")]
        public string TokenUri { get; set; } = "";

        [JsonPropertyName("client_email")]
        public string ClientEmail { get; set; } = "";

        [JsonPropertyName("private_key")]
        public string PrivateKey { get; set; } = "";
    }

    // A C# class to hold the token response from Google's token endpoint.
    private class TokenResponse
    {
        [JsonPropertyName("id_token")]
        public string IdToken { get; set; } = "";
    }

    /// <summary>
    /// The main entry point for the application.
    /// </summary>
    public static async Task Main(string[] args)
    {
        try
        {
            var options = ProcessArgs(args);
            if (options.KeyFile is not null && options.Audience is not null)
            {
                // 1. Load the service account key file.
                var keyFileContent = await File.ReadAllTextAsync(options.KeyFile);
                var serviceAccountKey = JsonSerializer.Deserialize<ServiceAccountKey>(
                    keyFileContent
                );

                if (serviceAccountKey.ClientEmail is null || serviceAccountKey.TokenUri is null)
                {
                    throw new Exception("That does not look like a Service Account key file.");
                }

                // 2. Generate the JWT.
                var jwt = GenerateJwt(serviceAccountKey, options.Audience);
                Console.WriteLine($"assertion: {jwt}\n");

                // pretty-print: extract the payload,decode it, parse the JSON, and then re-serialize it with indentation.
                using (
                    var jsonDocument = JsonDocument.Parse(
                        (new JwtSecurityTokenHandler()).ReadJwtToken(jwt).Payload.SerializeToJson()
                    )
                )
                {
                    Console.WriteLine(
                        "payload of the above:\n"
                            + JsonSerializer.Serialize(
                                jsonDocument.RootElement,
                                jsonSerializerOptions
                            )
                    );
                }

                // Console.WriteLine(
                //     "payload:\n" + JsonSerializer.Serialize(payloadJson, jsonSerializerOptions)
                // );

                // 3. Redeem the JWT for an ID token.
                var tokenResponse = await RedeemJwtForGoogleIdToken(
                    serviceAccountKey.TokenUri,
                    jwt
                );
                Console.WriteLine(
                    "\ntoken response:\n"
                        + JsonSerializer.Serialize(tokenResponse, jsonSerializerOptions)
                );

                // 4. Get and show token info.
                await ShowTokenInfo(tokenResponse.IdToken);
            }
            else
            {
                Usage();
            }
        }
        catch (Exception e)
        {
            Console.WriteLine($"Exception: {e}");
        }
    }

    /// <summary>
    /// Generates a signed JWT for authenticating to Google.
    /// </summary>
    private static string GenerateJwt(ServiceAccountKey serviceAccountKey, string audience)
    {
        var handler = new JwtSecurityTokenHandler();
        var now = DateTime.UtcNow;

        // The private key needs to be imported into an RSA object.
        var rsa = RSA.Create();
        // The key is in PKCS8 format, so we use ImportFromPkcs8.
        var privateKeyBytes = Convert.FromBase64String(
            serviceAccountKey
                .PrivateKey.Replace("-----BEGIN PRIVATE KEY-----", "")
                .Replace("-----END PRIVATE KEY-----", "")
                .Replace("\n", "")
        );
        rsa.ImportPkcs8PrivateKey(privateKeyBytes, out _);

        var securityKey = new RsaSecurityKey(rsa);
        var signingCredentials = new SigningCredentials(securityKey, SecurityAlgorithms.RsaSha256);

        var claims = new Dictionary<string, object> { { "target_audience", audience } };

        var tokenDescriptor = new SecurityTokenDescriptor
        {
            Issuer = serviceAccountKey.ClientEmail,
            Audience = serviceAccountKey.TokenUri,
            IssuedAt = now,
            Expires = now.AddMinutes(1),
            SigningCredentials = signingCredentials,
            Claims = claims,
        };
        var securityToken = handler.CreateToken(tokenDescriptor);
        return handler.WriteToken(securityToken);
    }

    /// <summary>
    /// Exchanges the signed JWT for a Google Cloud ID token.
    /// </summary>
    private static async Task<TokenResponse> RedeemJwtForGoogleIdToken(
        string tokenUri,
        string assertion
    )
    {
        using var httpClient = new HttpClient();
        const string grantType = "urn:ietf:params:oauth:grant-type:jwt-bearer";

        var content = new FormUrlEncodedContent(
            new[]
            {
                new KeyValuePair<string, string>("grant_type", grantType),
                new KeyValuePair<string, string>("assertion", assertion),
            }
        );

        var response = await httpClient.PostAsync(tokenUri, content);
        response.EnsureSuccessStatusCode();

        var responseStream = await response.Content.ReadAsStreamAsync();
        var tokenResponse = await JsonSerializer.DeserializeAsync<TokenResponse>(responseStream);

        if (tokenResponse is null)
        {
            throw new InvalidOperationException("Failed to deserialize token response.");
        }
        return tokenResponse;
    }

    /// <summary>
    /// Fetches and displays information about the given ID token.
    /// </summary>
    private static async Task ShowTokenInfo(string idToken)
    {
        using var httpClient = new HttpClient();
        var response = await httpClient.GetAsync(
            $"https://www.googleapis.com/oauth2/v3/tokeninfo?id_token={idToken}"
        );
        response.EnsureSuccessStatusCode();

        var jsonDocument = await JsonDocument.ParseAsync(
            await response.Content.ReadAsStreamAsync()
        );
        Console.WriteLine(
            "\ntoken info:\n"
                + JsonSerializer.Serialize(jsonDocument.RootElement, jsonSerializerOptions)
        );
    }

    /// <summary>
    /// Parses command-line arguments into an Options record.
    /// </summary>
    private static Options ProcessArgs(string[] args)
    {
        string keyFile = null;
        string audience = null;

        for (int i = 0; i < args.Length; i++)
        {
            if (args[i] == "--keyfile" && i + 1 < args.Length)
            {
                keyFile = args[++i];
            }
            else if (args[i] == "--audience" && i + 1 < args.Length)
            {
                audience = args[++i];
            }
            else if (args[i] == "-h" || args[i] == "--help")
            {
                return null;
            }
        }
        return new Options(keyFile, audience);
    }

    /// <summary>
    /// Prints the usage instructions for the application.
    /// </summary>
    private static void Usage()
    {
        var basename = Path.GetFileName(System.Reflection.Assembly.GetExecutingAssembly().Location);
        Console.WriteLine(
            $"usage:\n  dotnet run {basename} --keyfile SERVICE_ACCOUNT_KEYFILE --audience DESIRED_AUDIENCE\n"
        );
    }
}
