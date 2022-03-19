# Verification

There currently exists 4 layers of verifications that can be performed on a UPP(hash).

1. `Initial verification`: It checks that it has been stored on our backend. No further checks are performed. You may
   think about this as a quick check.
2. `Simple verification`: This verification checks the existence of the upp in our backend and additionally, it checks
   the "chain" and the validity of the "keys" (That the UPP can be verified by one of the available keys for the
   particular device/entity.)
3. `Upper verification`: This verification checks the existence of the upp in our backend, it checks the "chain" and the
   validity of the "keys" (That the UPP can be verified by one of the available keys for the particular device/entity)
   and retrieves the upper bounds or the closet blockchains transactions in the near future.
4. `Full verification`: This verification checks the existence of the upp in our backend, it checks the "chain" and the
   validity of the "keys" (That the UPP can be verified by one of the available keys for the particular device/entity)
   and retrieves the upper and lower bounds or the closet blockchains transactions in the near future and past.

# Verification Workflow

This diagram shows the 4 types of verifications that are currently supported.

![Anchoring](../.images/Verification.svg)

**Note**: Verification 1 only check UPP existence and unpacking. Verifications 2, 3, and 4, check for the validity of
the upp in terms of signature of the upp, chain and unpacking.

This system talks internally to the Identity Service: https://github.com/ubirch/ubirch-id-service to retrieve the
available keys, and to the cassandra storage, both for checking event logs, and paths to verifications.

* [Endpoints V1](V1.md)
* [Endpoints V2](V2.md)
* [Access Token For Endpoints V2](AccessToken.md)

# Anchoring Workflow

This workflow is a simplified version of the anchoring workflow. It shows the most important and relevant activities
that are performed when a UPP is received.

![Anchoring](../.images/Anchoring.svg)


