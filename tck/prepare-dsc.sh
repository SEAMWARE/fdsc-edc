#!/bin/bash

set -euo pipefail

PROPERTIES_FILE="config/tck.config"
TMP_FILE="$(mktemp)"

cp "$PROPERTIES_FILE" "$TMP_FILE"

PROVIDER_ID=$(curl -X 'POST' \
  'http://tm-forum-api.127.0.0.1.nip.io:8080/tmf-api/party/v4/organization' \
  -H 'accept: application/json;charset=utf-8' \
  -H 'Content-Type: application/json;charset=utf-8' \
  -d '{
  "name": "provider.io",
  "partyCharacteristic": [
    {
      "name": "did",
      "value": "did:web:provider.io"
    }
  ]
}' | jq .id -r); echo ${PROVIDER_ID}

CONSUMER_ID=$(curl -X 'POST' \
  'http://tm-forum-api.127.0.0.1.nip.io:8080/tmf-api/party/v4/organization' \
  -H 'accept: application/json;charset=utf-8' \
  -H 'Content-Type: application/json;charset=utf-8' \
  -d '{
  "name": "TCK_PARTICIPANT",
  "partyCharacteristic": [
    {
      "name": "did",
      "value": "did:web:fancy-marketplace.biz"
    }
  ]
}' | jq .id -r); echo ${CONSUMER_ID}

CATEGORY_ID=$(curl -X 'POST' \
  'http://tm-forum-api.127.0.0.1.nip.io:8080/tmf-api/productCatalogManagement/v4/category' \
  -H 'accept: application/json;charset=utf-8' \
  -H 'Content-Type: application/json;charset=utf-8' \
  -d '{
  "description": "Test Category",
  "name": "Test Category"
}' | jq .id -r); echo ${CATEGORY_ID}

CATALOG_ID=$(curl -X 'POST' \
  'http://tm-forum-api.127.0.0.1.nip.io:8080/tmf-api/productCatalogManagement/v4/catalog' \
  -H 'accept: application/json;charset=utf-8' \
  -H 'Content-Type: application/json;charset=utf-8' \
  -d "{
  \"description\": \"Test Catalog\",
  \"name\": \"Test Catalog\",
  \"category\": [
    {
        \"id\": \"${CATEGORY_ID}\"
    }
  ]
}" | jq .id -r); echo ${CATALOG_ID}

# Loop over lines ending with DATASETID
grep -E '^[A-Za-z0-9_]+DATASETID=' "$PROPERTIES_FILE" | while IFS='=' read -r key value; do
  echo "Processing $key=$value"

  org_key=$(echo ${key} | sed "s|DATASETID|OFFERID|")
  existing_external_key=$(grep "^${org_key}=" "$PROPERTIES_FILE" | cut -d'=' -f2- || true )

  if [ -z "$existing_external_key" ]; then
    echo "Empty external key"
    assetId=$(uuidgen)
    existing_external_key="CD123:${assetId}:123"
  else
    assetId=$(echo "$existing_external_key" | cut -d':' -f2)
  fi


  # Call API - create spec for dataset
  spec_id=$(curl -X 'POST' \
    'http://tm-forum-api.127.0.0.1.nip.io:8080/tmf-api/productCatalogManagement/v4/productSpecification' \
    -H 'accept: application/json;charset=utf-8' \
    -H 'Content-Type: application/json;charset=utf-8' \
    -d "{
           \"name\": \"Test Spec\",
           \"externalId\": \"${assetId}\",
           \"@schemaLocation\": \"https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/external-id.json\",
           \"productSpecCharacteristic\": [
               {
                   \"id\": \"endpointUrl\",
                   \"name\":\"Service Endpoint URL\",
                   \"valueType\":\"endpointUrl\",
                   \"productSpecCharacteristicValue\": [{
                       \"value\":\"https://the-test-service.org\",
                       \"isDefault\": true
                   }]
               },
               {
                   \"id\": \"endpointDescription\",
                   \"name\":\"Service Endpoint Description\",
                   \"valueType\":\"endpointDescription\",
                   \"productSpecCharacteristicValue\": [{
                       \"value\":\"The Test Service\"
                   }]
               },
               {
                 \"id\": \"serviceConfiguration\",
                 \"name\": \"Service config to be used in the credentials config service\",
                 \"valueType\": \"serviceConfiguration\",
                 \"productSpecCharacteristicValue\": [
                   {
                     \"isDefault\": true,
                     \"value\": {
                       \"defaultOidcScope\": \"openid\",
                       \"authorizationType\": \"FRONTEND_V2\",
                       \"oidcScopes\": {
                         \"openid\": {
                           \"credentials\": [
                             {
                               \"type\": \"LegalPersonCredential\",
                               \"trustedParticipantsLists\": [
                                {
                                  \"type\": \"ebsi\",
                                  \"url\": \"http://tir.127.0.0.1.nip.io\"
                                }
                               ],
                               \"trustedIssuersLists\": [\"http://trusted-issuers-list:8080\"],
                               \"jwtInclusion\": {
                                 \"enabled\": true,
                                 \"fullInclusion\": true
                               }
                             }
                           ],
                           \"dcql\": {
                             \"credentials\": [
                               {
                                 \"id\": \"legal-person-query\",
                                 \"format\": \"dc+sd-jwt\",
                                 \"multiple\": false,
                                 \"claims\": [
                                   {
                                     \"id\": \"name-claim\",
                                     \"path\": [\"firstName\"]
                                   }
                                 ],
                                 \"meta\": {
                                   \"vct_values\": [\"LegalPersonCredential\"]
                                 }
                               }
                             ]
                           }
                         }
                       }
                     }
                   }
                 ]
               }
           ]
       }" | jq '.id' -r)

  access_policy_id=$(uuidgen)
  contract_policy_id=$(uuidgen)

  new_value=$(curl -X 'POST' \
               'http://tm-forum-api.127.0.0.1.nip.io:8080/tmf-api/productCatalogManagement/v4/productOffering' \
               -H 'accept: application/json;charset=utf-8' \
               -H 'Content-Type: application/json;charset=utf-8' \
               -d "{
                          \"name\": \"Test Offering\",
                          \"description\": \"Test Offering description\",
                          \"isBundle\": false,
                          \"isSellable\": true,
                          \"lifecycleStatus\": \"Active\",
                          \"@schemaLocation\": \"https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/external-id.json\",
                          \"externalId\": \"${existing_external_key}\",
                          \"productSpecification\":
                              {
                                  \"id\": \"${spec_id}\",
                                  \"name\":\"The Test Spec\"
                              },
                          \"category\": [{
                              \"id\": \"${CATEGORY_ID}\"
                          }],
                          \"productOfferingTerm\": [
                            {
                              \"name\": \"edc:contractDefinition\",
                              \"@schemaLocation\": \"https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/contract-definition.json\",
                              \"contractPolicy\": {
                                \"odrl:uid\": \"${contract_policy_id}\",
                                \"assigner\": \"did:web:provider.io\",
                                \"@type\": {
                                  \"@policytype\": \"offer\"
                                },
                                \"permissions\": [{
                                  \"action\": {
                                    \"type\": \"http://www.w3.org/ns/odrl/2/use\"
                                  },
                                  \"edctype\": \"dataspaceconnector:permission\"
                                }]
                              },
                              \"accessPolicy\": {
                                \"odrl:uid\": \"${access_policy_id}\",
                                \"assigner\": \"did:web:provider.io\",
                                \"permissions\": [{
                                  \"action\": {
                                    \"type\": \"http://www.w3.org/ns/odrl/2/use\"
                                  },
                                  \"edctype\": \"dataspaceconnector:permission\"
                                }],
                                \"@type\": {
                                  \"@policytype\": \"offer\"
                                }
                              }
                            }
                          ]
                      }" | jq '.id' -r)

  if [ -z "$new_value" ] || [ "$new_value" = "null" ]; then
    echo "Skipping $key"
    continue
  fi

  echo "Updating $key=$assetId"

#  # Escape slashes for sed replacement
#  escaped_new_value=$(printf '%s\n' "$assetId" | sed 's/[\/&]/\\&/g')
#
#  # Update in-place in temp file
#  sed -i "s|^${key}=.*|${key}=${escaped_new_value}|" "$TMP_FILE"
#
#  offer_key=$(echo ${key} | sed "s|DATASETID|OFFERID|")
#
#  echo "Updating $offer_key=$existing_external_key"
#
#  # Escape slashes for sed replacement
#  escaped_new_value=$(printf '%s\n' "$existing_external_key" | sed 's/[\/&]/\\&/g')
#
#  # Update in-place in temp file
#  sed -i "s|^${offer_key}=.*|${offer_key}=${escaped_new_value}|" "$TMP_FILE"
done

# Loop over lines ending with AGREEMENTID
grep -E '^[A-Za-z0-9_]+AGREEMENTID=' "$PROPERTIES_FILE" | while IFS='=' read -r key value; do
  echo "Processing $key=$value"

  assetId=$(uuidgen)
  external_key="CD123:${assetId}:123"

  if [[ "$key" != *"_C_"* ]]; then
      # provider side - assets need to exist

        # Call API - create spec for dataset
      spec_id=$(curl -X 'POST' \
        'http://tm-forum-api.127.0.0.1.nip.io:8080/tmf-api/productCatalogManagement/v4/productSpecification' \
        -H 'accept: application/json;charset=utf-8' \
        -H 'Content-Type: application/json;charset=utf-8' \
        -d "{
              \"name\": \"Test Spec\",
              \"productSpecCharacteristic\": [
                  {
                      \"id\": \"endpointUrl\",
                      \"name\":\"Service Endpoint URL\",
                      \"valueType\":\"endpointUrl\",
                      \"productSpecCharacteristicValue\": [{
                          \"value\":\"data-service-scorpio:9090\",
                          \"isDefault\": true
                      }]
                  },
                  {
                      \"id\": \"endpointDescription\",
                      \"name\":\"Service Endpoint Description\",
                      \"valueType\":\"endpointDescription\",
                      \"productSpecCharacteristicValue\": [{
                          \"value\":\"The Test Service\"
                      }]
                  },
                  {
                    \"id\": \"serviceConfiguration\",
                    \"name\": \"Service config to be used in the credentials config service\",
                    \"valueType\": \"serviceConfiguration\",
                    \"productSpecCharacteristicValue\": [
                      {
                        \"isDefault\": true,
                        \"value\": {
                          \"defaultOidcScope\": \"openid\",
                          \"authorizationType\": \"FRONTEND_V2\",
                          \"oidcScopes\": {
                            \"openid\": {
                              \"credentials\": [
                                {
                                  \"type\": \"LegalPersonCredential\",
                                   \"trustedParticipantsLists\": [
                                    {
                                      \"type\": \"ebsi\",
                                      \"url\": \"http://tir.127.0.0.1.nip.io\"
                                    }
                                   ],
                                  \"trustedIssuersLists\": [\"http://trusted-issuers-list:8080\"],
                                  \"jwtInclusion\": {
                                    \"enabled\": true,
                                    \"fullInclusion\": true
                                  }
                                }
                              ],
                              \"dcql\": {
                                \"credentials\": [
                                  {
                                    \"id\": \"legal-person-query\",
                                    \"format\": \"dc+sd-jwt\",
                                    \"multiple\": false,
                                    \"claims\": [
                                      {
                                        \"id\": \"name-claim\",
                                        \"path\": [\"firstName\"]
                                      }
                                    ],
                                    \"meta\": {
                                      \"vct_values\": [\"LegalPersonCredential\"]
                                    }
                                  }
                                ]
                              }
                            }
                          }
                        }
                      }
                    ]
                  }
              ]
          }" | jq '.id' -r)


      access_policy_id=$(uuidgen)
      contract_policy_id=$(uuidgen)


      offering_id=$(curl -X 'POST' \
         'http://tm-forum-api.127.0.0.1.nip.io:8080/tmf-api/productCatalogManagement/v4/productOffering' \
         -H 'accept: application/json;charset=utf-8' \
         -H 'Content-Type: application/json;charset=utf-8' \
         -d "{
                    \"name\": \"Test Offering\",
                    \"description\": \"Test Offering description\",
                    \"isBundle\": false,
                    \"isSellable\": true,
                    \"lifecycleStatus\": \"Active\",
                    \"@schemaLocation\": \"https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/external-id.json\",
                    \"externalId\": \"${external_key}\",
                    \"productSpecification\":
                        {
                            \"id\": \"${spec_id}\",
                            \"name\":\"The Test Spec\"
                        },
                    \"category\": [{
                        \"id\": \"${CATEGORY_ID}\"
                    }],
                    \"productOfferingTerm\": [
                      {
                        \"name\": \"edc:contractDefinition\",
                        \"@schemaLocation\": \"https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/contract-definition.json\",
                        \"contractPolicy\": {
                          \"odrl:uid\": \"${contract_policy_id}\",
                          \"assigner\": \"did:web:provider.io\",
                          \"@type\": {
                            \"@policytype\": \"offer\"
                          },
                          \"permissions\": [{
                            \"action\": {
                              \"type\": \"http://www.w3.org/ns/odrl/2/use\"
                            },
                            \"edctype\": \"dataspaceconnector:permission\"
                          }]
                        },
                        \"accessPolicy\": {
                          \"odrl:uid\": \"${access_policy_id}\",
                          \"assigner\": \"did:web:provider.io\",
                          \"permissions\": [{
                            \"action\": {
                              \"type\": \"http://www.w3.org/ns/odrl/2/use\"
                            },
                            \"edctype\": \"dataspaceconnector:permission\"
                          }],
                          \"@type\": {
                            \"@policytype\": \"offer\"
                          }
                        }
                      }
                    ]
                }" | jq '.id' -r)
  fi

  existing_external_key=$(grep "^${key}=" "$PROPERTIES_FILE" | cut -d'=' -f2- || true )


  if [[ "$key" != *"_C_"* ]]; then
    # Call API
      product_id=$(curl -X 'POST' \
        'http://tm-forum-api.127.0.0.1.nip.io:8080/tmf-api/productInventory/v4/product' \
        -H 'accept: application/json;charset=utf-8' \
        -H 'Content-Type: application/json;charset=utf-8' \
        -d "{
              \"name\": \"testProduct\",
              \"status\": \"active\",
              \"@schemaLocation\": \"https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/external-id.json\",
              \"externalId\":\"${assetId}\",
              \"relatedParty\": [
                {
                  \"id\": \"$PROVIDER_ID\",
                  \"role\": \"Provider\"
                },
                {
                  \"id\": \"$CONSUMER_ID\",
                  \"role\": \"Consumer\"
                }
              ],
              \"productOffering\": {
                \"id\": \"${offering_id}\"
              }
          }" | jq '.id' -r)
  else
    # Call API
    product_id=$(curl -X 'POST' \
      'http://tm-forum-api.127.0.0.1.nip.io:8080/tmf-api/productInventory/v4/product' \
      -H 'accept: application/json;charset=utf-8' \
      -H 'Content-Type: application/json;charset=utf-8' \
      -d "{
            \"name\": \"testProduct\",
            \"status\": \"active\",
            \"@schemaLocation\": \"https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/external-id.json\",
            \"externalId\":\"${assetId}\",
            \"relatedParty\": [
              {
                \"id\": \"$PROVIDER_ID\",
                \"role\": \"Provider\"
              },
              {
                \"id\": \"$CONSUMER_ID\",
                \"role\": \"Consumer\"
              }
            ]
        }" | jq '.id' -r)
  fi


  # Call API
  new_value=$(curl -X 'POST' \
    'http://tm-forum-api.127.0.0.1.nip.io:8080/tmf-api/agreementManagement/v4/agreement' \
    -H 'accept: application/json;charset=utf-8' \
    -H 'Content-Type: application/json;charset=utf-8' \
    -d "{
          \"agreementType\": \"test\",
          \"name\": \"testAgreement\",
          \"@schemaLocation\": \"https://raw.githubusercontent.com/wistefan/edc-dsc/refs/heads/init/external-id.json\",
          \"externalId\":\"${existing_external_key}\",
          \"agreementItem\": [
            {
              \"product\": {
                \"id\": \"${product_id}\"
              }
            }
          ],
          \"characteristic\": [
              {
                  \"name\": \"signing-date\",
                  \"value\": 1763130563
              },
              {
                  \"name\": \"provider-id\",
                  \"value\": \"did:web:provider.io\"
              },
              {
                 \"name\": \"consumer-id\",
                 \"value\": \"TCK_PARTICIPANT\"
              },
              {
                \"name\": \"policy\",
                \"value\": {
                  \"permissions\": [{
                    \"edctype\": \"dataspaceconnector:permission\",
                    \"action\": {
                      \"type\": \"use\"
                    }
                  },
                  {
                    \"edctype\": \"dataspaceconnector:permission\",
                    \"action\": {
                      \"type\": \"use\"
                    }
                  }]
                }
              },
              {
                \"name\": \"asset-id\",
                \"value\": \"${assetId}\"
              }
          ]
      }" | jq '.id' -r)

  if [ -z "$new_value" ] || [ "$new_value" = "null" ]; then
    echo "Skipping $key"
    continue
  fi

done

# Replace original file
mv "$TMP_FILE" "$PROPERTIES_FILE"

chmod a+rw $PROPERTIES_FILE