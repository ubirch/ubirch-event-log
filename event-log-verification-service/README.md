# Verification 

**Note**: For the following examples, you should take into account the environment you are working on. DEV, DEMO or PRO and change the URL accordingly.

**Note**: Verifications 2, 3, and 4, check for the validity of the upp in terms of signature of the upp, chain and unpacking.

This system talks internally to the Identity Service: https://github.com/ubirch/ubirch-id-service to retrieve the available keys.


## (1) https://verify.demo.ubirch.com/api/upp

This endpoint basically only queries for the existence of the upp. It checks that it has been stored on our backend. No further checks are performed. You may think about this as a quick check.

_Example_

```shell script
curl -s -X POST https://verify.demo.ubirch.com/api/upp -d '24gpRTHckCjMTMxR17dBHxa8cKMm8uZ+I5HsFCOIbqU='
{
  "upp": "liPEEE9rZKelyUg3hsANMryOA8DEQHbKEbBFewQjK/9M8Df9wvDBLT9EkHIrtdyTs/LYhdega072sKOoFeGs4uq6Gfb986vjikaXiKdxeocZu/vUp6QAxCDbiClFMdyQKMxMzFHXt0EfFrxwoyby5n4jkewUI4hupcRAn8z4LHBlUtFORPDtp8bsQ4ANg+DogyAFLcsLqmW+28SWliB4Qmlv3Zglr/MH87UsIWR/+/Jx+ePhv/CSkSNVBg==",
  "prev": null,
  "anchors": null
}
```


## (2) https://verify.demo.ubirch.com/api/upp/verify

This query checks for the existence of the upp in our backend and additionally, it checks the "chain" and the validity of the "keys" (That the UPP can be verified by one of the available keys for the particualar device/entity.)

_Example_

```shell script
curl -s -X POST https://verify.demo.ubirch.com/api/upp/verify -d '24gpRTHckCjMTMxR17dBHxa8cKMm8uZ+I5HsFCOIbqU=' 
{
  "upp": "liPEEE9rZKelyUg3hsANMryOA8DEQHbKEbBFewQjK/9M8Df9wvDBLT9EkHIrtdyTs/LYhdega072sKOoFeGs4uq6Gfb986vjikaXiKdxeocZu/vUp6QAxCDbiClFMdyQKMxMzFHXt0EfFrxwoyby5n4jkewUI4hupcRAn8z4LHBlUtFORPDtp8bsQ4ANg+DogyAFLcsLqmW+28SWliB4Qmlv3Zglr/MH87UsIWR/+/Jx+ePhv/CSkSNVBg==",
  "prev": "liPEEE9rZKelyUg3hsANMryOA8DEQBwmU0CFQ4RjwsEBXrmCvKL/fApnhlTriY040ERfAQyssqsgfSaJq1Oxd+3kGHElAfbGVZxAWFZ6uE7fQk9bl7wAxCBkdIWpCIhc6TIJh+zI1u7nOXA73GOgMSdlZ+zMAirteMRAdsoRsEV7BCMr/0zwN/3C8MEtP0SQciu13JOz8tiF16BrTvawo6gV4azi6roZ9v3zq+OKRpeIp3F6hxm7+9SnpA==",
  "anchors": null
}
```

**NOTE**: (1) and (2) will never return a value for the anchors fields.

## (3) https://verify.demo.ubirch.com/api/upp/verify/anchor

This query checks for the existence of the upp in our backend, it checks the "chain" and the validity of the "keys" (That the UPP can be verified by one of the available keys for the particualar device/entity) and retrieves the upper bounds or the closet blockchains transactions in the near future. You can get a compacted version or full version based on the params below.

optional params

```
response_form: Provided the shortest paths

response_form=anchors_no_path – This is the default if not provided
response_form=anchors_with_path

blockchain_info: Decorates the normal blockchain object with extra info gotten from the anchoring systems.

blockchain_info=normal – This is the default if not provided

blockchain_info=ext
```

Example with default params

```shell script
curl -s -X POST https://verify.dev.ubirch.com/api/upp/verify/anchor -d 'oPV/aJsximYq2DbduTEarm8Jhae4uy61xOB6JIAACnFBCDJjJjBvz1sQNlqEfEAeCq1q5Kl1bv6KGz1y2wKQRw==' 
{
  "upp": "lSLEEOl+FgxhF1uJrJgVrrUmVeAAxECg9X9omzGKZirYNt25MRqubwmFp7i7LrXE4HokgAAKcUEIMmMmMG/PWxA2WoR8QB4KrWrkqXVu/oobPXLbApBHxEBQHgrfS+yaIC2XDoggA3R52cnWg2k1qkdDDS4iUUInZoIBd5MpsWGj55W5tcjVKFlP84T5Z2ocG0YdbSu7qjoM",
  "prev": null,
  "anchors": [
    {
      "label": "PUBLIC_CHAIN",
      "properties": {
        "timestamp": "2019-10-30T20:35:01.883Z",
        "hash": "WHHLUYPNMUZHXZQJHRPFJU9AADXWUUJRXMV9MIEPKTWFYJBPM9QI9WK9WXUEXZATGPUUB9IFQXICA9999",
        "public_chain": "IOTA_TESTNET_IOTA_TESTNET_NETWORK",
        "prev_hash": "0a4303cf15ade07b6987dd31aba5cf4a5e60058644176c22122e91e7b94e90e59ccd362dfc88d3e84baa5eef9aa7b9dd0cdf6a6151dceb7a9459bd090925b233",
        "type": "PUBLIC_CHAIN"
      }
    }
  ]
}
```
                       
Example with paths and extra info for blockchains.

```shell script
curl -s -X POST 'https://verify.dev.ubirch.com/api/upp/verify/anchor?response_form=anchors_with_path&blockchain_info=ext' -d 'oPV/aJsximYq2DbduTEarm8Jhae4uy61xOB6JIAACnFBCDJjJjBvz1sQNlqEfEAeCq1q5Kl1bv6KGz1y2wKQRw=='
{
    "upp": "lSLEEOl+FgxhF1uJrJgVrrUmVeAAxECg9X9omzGKZirYNt25MRqubwmFp7i7LrXE4HokgAAKcUEIMmMmMG/PWxA2WoR8QB4KrWrkqXVu/oobPXLbApBHxEBQHgrfS+yaIC2XDoggA3R52cnWg2k1qkdDDS4iUUInZoIBd5MpsWGj55W5tcjVKFlP84T5Z2ocG0YdbSu7qjoM",
    "prev": null,
    "anchors": {
        "shortest_path": [
            {
                "label": "UPP",
                "properties": {
                    "timestamp": "2019-10-30T20:33:34.919Z",
                    "next_hash": "fa60e26ecc3558a5fa567108eb652b3da3dfa4b2c9cb2f6588a4f51c726166540b0e2728c8a9653337a204577f951dd62ae45d431d01c7901109c3f66731faa8",
                    "signature": "UB4K30vsmiAtlw6IIAN0ednJ1oNpNapHQw0uIlFCJ2aCAXeTKbFho+eVubXI1ShZT/OE+WdqHBtGHW0ru6o6DA==",
                    "hash": "oPV/aJsximYq2DbduTEarm8Jhae4uy61xOB6JIAACnFBCDJjJjBvz1sQNlqEfEAeCq1q5Kl1bv6KGz1y2wKQRw==",
                    "prev_hash": "",
                    "type": "UPP"
                }
            },
            {
                "label": "SLAVE_TREE",
                "properties": {
                    "timestamp": "2019-10-30T20:33:36.930Z",
                    "next_hash": "0a4303cf15ade07b6987dd31aba5cf4a5e60058644176c22122e91e7b94e90e59ccd362dfc88d3e84baa5eef9aa7b9dd0cdf6a6151dceb7a9459bd090925b233",
                    "hash": "fa60e26ecc3558a5fa567108eb652b3da3dfa4b2c9cb2f6588a4f51c726166540b0e2728c8a9653337a204577f951dd62ae45d431d01c7901109c3f66731faa8",
                    "prev_hash": "oPV/aJsximYq2DbduTEarm8Jhae4uy61xOB6JIAACnFBCDJjJjBvz1sQNlqEfEAeCq1q5Kl1bv6KGz1y2wKQRw==",
                    "type": "SLAVE_TREE"
                }
            },
            {
                "label": "MASTER_TREE",
                "properties": {
                    "timestamp": "2019-10-30T20:34:05.474Z",
                    "next_hash": "WHHLUYPNMUZHXZQJHRPFJU9AADXWUUJRXMV9MIEPKTWFYJBPM9QI9WK9WXUEXZATGPUUB9IFQXICA9999",
                    "hash": "0a4303cf15ade07b6987dd31aba5cf4a5e60058644176c22122e91e7b94e90e59ccd362dfc88d3e84baa5eef9aa7b9dd0cdf6a6151dceb7a9459bd090925b233",
                    "prev_hash": "fa60e26ecc3558a5fa567108eb652b3da3dfa4b2c9cb2f6588a4f51c726166540b0e2728c8a9653337a204577f951dd62ae45d431d01c7901109c3f66731faa8",
                    "type": "MASTER_TREE"
                }
            }
        ],
        "blockchains": [
            {
                "label": "PUBLIC_CHAIN",
                "properties": {
                    "timestamp": "2019-10-30T20:35:01.883Z",
                    "network_info": "IOTA Testnet Network",
                    "network_type": "testnet",
                    "txid": "WHHLUYPNMUZHXZQJHRPFJU9AADXWUUJRXMV9MIEPKTWFYJBPM9QI9WK9WXUEXZATGPUUB9IFQXICA9999",
                    "hash": "WHHLUYPNMUZHXZQJHRPFJU9AADXWUUJRXMV9MIEPKTWFYJBPM9QI9WK9WXUEXZATGPUUB9IFQXICA9999",
                    "status": "added",
                    "public_chain": "IOTA_TESTNET_IOTA_TESTNET_NETWORK",
                    "blockchain": "iota",
                    "message": "0a4303cf15ade07b6987dd31aba5cf4a5e60058644176c22122e91e7b94e90e59ccd362dfc88d3e84baa5eef9aa7b9dd0cdf6a6151dceb7a9459bd090925b233",
                    "prev_hash": "0a4303cf15ade07b6987dd31aba5cf4a5e60058644176c22122e91e7b94e90e59ccd362dfc88d3e84baa5eef9aa7b9dd0cdf6a6151dceb7a9459bd090925b233",
                    "type": "PUBLIC_CHAIN",
                    "created": "2019-10-30T20:35:01.878372"
                }
            }
        ]
    }
}
```

## (4) https://verify.dev.ubirch.com/api/upp/verify/record

This query checks for the existence of the upp in our backend and additionally, it checks the "chain" and the validity of the "keys" (That the UPP can be verified by one of the available keys for the particualar device/entity.)

This query checks for the existence of the upp in our backend, it checks the "chain" and the validity of the "keys" (That the UPP can be verified by one of the available keys for the particualar device/entity) and retrieves the upper and lower bounds or the closet blockchains transactions in the near future and past. You can get a compacted version or full version based on the params below.

optional params

```
response_form: Provided the shortest paths

response_form=anchors_no_path – This is the default if not provided
response_form=anchors_with_path

blockchain_info: Decorates the normal blockchain object with extra info gotten from the anchoring systems.

blockchain_info=normal – This is the default if not provided

blockchain_info=ext
```


_Example with default params_

```shell script
curl -s -X POST 'https://verify.dev.ubirch.com/api/upp/verify/record' -d 'oPV/aJsximYq2DbduTEarm8Jhae4uy61xOB6JIAACnFBCDJjJjBvz1sQNlqEfEAeCq1q5Kl1bv6KGz1y2wKQRw=='
{
    "upp": "lSLEEOl+FgxhF1uJrJgVrrUmVeAAxECg9X9omzGKZirYNt25MRqubwmFp7i7LrXE4HokgAAKcUEIMmMmMG/PWxA2WoR8QB4KrWrkqXVu/oobPXLbApBHxEBQHgrfS+yaIC2XDoggA3R52cnWg2k1qkdDDS4iUUInZoIBd5MpsWGj55W5tcjVKFlP84T5Z2ocG0YdbSu7qjoM",
    "prev": null,
    "anchors": {
        "upper_blockchains": [
            {
                "label": "PUBLIC_CHAIN",
                "properties": {
                    "timestamp": "2019-10-30T20:35:01.883Z",
                    "hash": "WHHLUYPNMUZHXZQJHRPFJU9AADXWUUJRXMV9MIEPKTWFYJBPM9QI9WK9WXUEXZATGPUUB9IFQXICA9999",
                    "public_chain": "IOTA_TESTNET_IOTA_TESTNET_NETWORK",
                    "prev_hash": "0a4303cf15ade07b6987dd31aba5cf4a5e60058644176c22122e91e7b94e90e59ccd362dfc88d3e84baa5eef9aa7b9dd0cdf6a6151dceb7a9459bd090925b233",
                    "type": "PUBLIC_CHAIN"
                }
            }
        ],
        "lower_blockchains": [
            {
                "label": "PUBLIC_CHAIN",
                "properties": {
                    "timestamp": "2019-10-30T20:33:03.982Z",
                    "hash": "MBCRQSNGBLVYHMJBKJHGFKFIDKVTFJHSMWUFPSCNOCUUIKQNML9H9KYQWBXUKCKNZIIOSUXPDORQZ9999",
                    "public_chain": "IOTA_TESTNET_IOTA_TESTNET_NETWORK",
                    "prev_hash": "ca96ca76c59753c27a100780d857bfdefe448ee16aa51aef6b3aa766bf28bb1e5bf355566124e830ef6641ef34f33a6b475641f4836dcbfe7b02c6edd6136eac",
                    "type": "PUBLIC_CHAIN"
                }
            }
        ]
    }
}
```


Example with paths and extra info for blockchains

```shell script
curl -s -X POST 'https://verify.dev.ubirch.com/api/upp/verify/record?response_form=anchors_with_path&blockchain_info=ext' -d 'oPV/aJsximYq2DbduTEarm8Jhae4uy61xOB6JIAACnFBCDJjJjBvz1sQNlqEfEAeCq1q5Kl1bv6KGz1y2wKQRw=='
{
    "upp": "lSLEEOl+FgxhF1uJrJgVrrUmVeAAxECg9X9omzGKZirYNt25MRqubwmFp7i7LrXE4HokgAAKcUEIMmMmMG/PWxA2WoR8QB4KrWrkqXVu/oobPXLbApBHxEBQHgrfS+yaIC2XDoggA3R52cnWg2k1qkdDDS4iUUInZoIBd5MpsWGj55W5tcjVKFlP84T5Z2ocG0YdbSu7qjoM",
    "prev": null,
    "anchors": {
        "upper_path": [
            {
                "label": "UPP",
                "properties": {
                    "timestamp": "2019-10-30T20:33:34.919Z",
                    "next_hash": "fa60e26ecc3558a5fa567108eb652b3da3dfa4b2c9cb2f6588a4f51c726166540b0e2728c8a9653337a204577f951dd62ae45d431d01c7901109c3f66731faa8",
                    "signature": "UB4K30vsmiAtlw6IIAN0ednJ1oNpNapHQw0uIlFCJ2aCAXeTKbFho+eVubXI1ShZT/OE+WdqHBtGHW0ru6o6DA==",
                    "hash": "oPV/aJsximYq2DbduTEarm8Jhae4uy61xOB6JIAACnFBCDJjJjBvz1sQNlqEfEAeCq1q5Kl1bv6KGz1y2wKQRw==",
                    "prev_hash": "",
                    "type": "UPP"
                }
            },
            {
                "label": "SLAVE_TREE",
                "properties": {
                    "timestamp": "2019-10-30T20:33:36.930Z",
                    "next_hash": "0a4303cf15ade07b6987dd31aba5cf4a5e60058644176c22122e91e7b94e90e59ccd362dfc88d3e84baa5eef9aa7b9dd0cdf6a6151dceb7a9459bd090925b233",
                    "hash": "fa60e26ecc3558a5fa567108eb652b3da3dfa4b2c9cb2f6588a4f51c726166540b0e2728c8a9653337a204577f951dd62ae45d431d01c7901109c3f66731faa8",
                    "prev_hash": "oPV/aJsximYq2DbduTEarm8Jhae4uy61xOB6JIAACnFBCDJjJjBvz1sQNlqEfEAeCq1q5Kl1bv6KGz1y2wKQRw==",
                    "type": "SLAVE_TREE"
                }
            },
            {
                "label": "MASTER_TREE",
                "properties": {
                    "timestamp": "2019-10-30T20:34:05.474Z",
                    "next_hash": "WHHLUYPNMUZHXZQJHRPFJU9AADXWUUJRXMV9MIEPKTWFYJBPM9QI9WK9WXUEXZATGPUUB9IFQXICA9999",
                    "hash": "0a4303cf15ade07b6987dd31aba5cf4a5e60058644176c22122e91e7b94e90e59ccd362dfc88d3e84baa5eef9aa7b9dd0cdf6a6151dceb7a9459bd090925b233",
                    "prev_hash": "fa60e26ecc3558a5fa567108eb652b3da3dfa4b2c9cb2f6588a4f51c726166540b0e2728c8a9653337a204577f951dd62ae45d431d01c7901109c3f66731faa8",
                    "type": "MASTER_TREE"
                }
            }
        ],
        "upper_blockchains": [
            {
                "label": "PUBLIC_CHAIN",
                "properties": {
                    "timestamp": "2019-10-30T20:35:01.883Z",
                    "network_info": "IOTA Testnet Network",
                    "network_type": "testnet",
                    "txid": "WHHLUYPNMUZHXZQJHRPFJU9AADXWUUJRXMV9MIEPKTWFYJBPM9QI9WK9WXUEXZATGPUUB9IFQXICA9999",
                    "hash": "WHHLUYPNMUZHXZQJHRPFJU9AADXWUUJRXMV9MIEPKTWFYJBPM9QI9WK9WXUEXZATGPUUB9IFQXICA9999",
                    "status": "added",
                    "public_chain": "IOTA_TESTNET_IOTA_TESTNET_NETWORK",
                    "blockchain": "iota",
                    "message": "0a4303cf15ade07b6987dd31aba5cf4a5e60058644176c22122e91e7b94e90e59ccd362dfc88d3e84baa5eef9aa7b9dd0cdf6a6151dceb7a9459bd090925b233",
                    "prev_hash": "0a4303cf15ade07b6987dd31aba5cf4a5e60058644176c22122e91e7b94e90e59ccd362dfc88d3e84baa5eef9aa7b9dd0cdf6a6151dceb7a9459bd090925b233",
                    "type": "PUBLIC_CHAIN",
                    "created": "2019-10-30T20:35:01.878372"
                }
            }
        ],
        "lower_path": [
            {
                "label": "MASTER_TREE",
                "properties": {
                    "timestamp": "2019-10-30T20:34:05.474Z",
                    "next_hash": "375f8683023272f4b31afd134411daddee94212d1f70f376c15ae23979207f26ece737f7ede575dd6b0b088cb154eb25a1ba09d35e51cf05b9bceb3e5f4e765e",
                    "hash": "0a4303cf15ade07b6987dd31aba5cf4a5e60058644176c22122e91e7b94e90e59ccd362dfc88d3e84baa5eef9aa7b9dd0cdf6a6151dceb7a9459bd090925b233",
                    "prev_hash": "",
                    "type": "MASTER_TREE"
                }
            },
            {
                "label": "MASTER_TREE",
                "properties": {
                    "timestamp": "2019-10-30T20:33:38.828Z",
                    "next_hash": "ca96ca76c59753c27a100780d857bfdefe448ee16aa51aef6b3aa766bf28bb1e5bf355566124e830ef6641ef34f33a6b475641f4836dcbfe7b02c6edd6136eac",
                    "hash": "375f8683023272f4b31afd134411daddee94212d1f70f376c15ae23979207f26ece737f7ede575dd6b0b088cb154eb25a1ba09d35e51cf05b9bceb3e5f4e765e",
                    "prev_hash": "0a4303cf15ade07b6987dd31aba5cf4a5e60058644176c22122e91e7b94e90e59ccd362dfc88d3e84baa5eef9aa7b9dd0cdf6a6151dceb7a9459bd090925b233",
                    "type": "MASTER_TREE"
                }
            },
            {
                "label": "MASTER_TREE",
                "properties": {
                    "timestamp": "2019-10-30T20:32:38.819Z",
                    "next_hash": "MBCRQSNGBLVYHMJBKJHGFKFIDKVTFJHSMWUFPSCNOCUUIKQNML9H9KYQWBXUKCKNZIIOSUXPDORQZ9999",
                    "hash": "ca96ca76c59753c27a100780d857bfdefe448ee16aa51aef6b3aa766bf28bb1e5bf355566124e830ef6641ef34f33a6b475641f4836dcbfe7b02c6edd6136eac",
                    "prev_hash": "375f8683023272f4b31afd134411daddee94212d1f70f376c15ae23979207f26ece737f7ede575dd6b0b088cb154eb25a1ba09d35e51cf05b9bceb3e5f4e765e",
                    "type": "MASTER_TREE"
                }
            }
        ],
        "lower_blockchains": [
            {
                "label": "PUBLIC_CHAIN",
                "properties": {
                    "timestamp": "2019-10-30T20:33:03.982Z",
                    "network_info": "IOTA Testnet Network",
                    "network_type": "testnet",
                    "txid": "MBCRQSNGBLVYHMJBKJHGFKFIDKVTFJHSMWUFPSCNOCUUIKQNML9H9KYQWBXUKCKNZIIOSUXPDORQZ9999",
                    "hash": "MBCRQSNGBLVYHMJBKJHGFKFIDKVTFJHSMWUFPSCNOCUUIKQNML9H9KYQWBXUKCKNZIIOSUXPDORQZ9999",
                    "status": "added",
                    "public_chain": "IOTA_TESTNET_IOTA_TESTNET_NETWORK",
                    "blockchain": "iota",
                    "message": "ca96ca76c59753c27a100780d857bfdefe448ee16aa51aef6b3aa766bf28bb1e5bf355566124e830ef6641ef34f33a6b475641f4836dcbfe7b02c6edd6136eac",
                    "prev_hash": "ca96ca76c59753c27a100780d857bfdefe448ee16aa51aef6b3aa766bf28bb1e5bf355566124e830ef6641ef34f33a6b475641f4836dcbfe7b02c6edd6136eac",
                    "type": "PUBLIC_CHAIN",
                    "created": "2019-10-30T20:33:03.977992"
                }
            }
        ]
    }
}
```

