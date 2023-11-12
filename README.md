# Implementation of AWS SRP authentication

This class authenticates against a cognito identity pool to obtain
an token suitable for authenticating web access in the pool.

It is a single class with constructor.

public AwsSRPAuthSupport(String companyId,
 String userPoolId,
 String userPoolClientId,
 String region,
 String secretKey,
 String username,
 String password,
 long timeBeforeExpiryToRefresh,
 software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient cognitoIdp)

and a single public method

public String getToken()
