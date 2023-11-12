# Implementation of AWS SRP authentication

This class authenticates against a cognito identity pool to obtain
a token suitable for authenticating web access in the pool.

It is a single class with constructor.

```
public AwsSRPAuthSupport(String companyId,
 String userPoolId,
 String userPoolClientId,
 String region,
 String secretKey,
 String username,
 String password,
 long timeBeforeExpiryToRefresh)
```

and a single public method

```
public String getToken()
```

This on first use authenticates and returns a token,

each subsequentcall returns the same token until call that occurs after
timeBeforeExpiryToRefresh seconds before the token expires when a new
authentication occurs and a new token is returned.


