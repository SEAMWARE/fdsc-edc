

# TestRequestVO


## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
|**method** | [**MethodEnum**](#MethodEnum) |  |  [optional] |
|**host** | **String** |  |  [optional] |
|**path** | **String** |  |  [optional] |
|**protocol** | [**ProtocolEnum**](#ProtocolEnum) |  |  [optional] |
|**body** | **Object** |  |  [optional] |
|**headers** | [**HeadersVO**](HeadersVO.md) |  |  [optional] |



## Enum: MethodEnum

| Name | Value |
|---- | -----|
| POST | &quot;POST&quot; |
| PATCH | &quot;PATCH&quot; |
| PUT | &quot;PUT&quot; |
| GET | &quot;GET&quot; |
| DELETE | &quot;DELETE&quot; |



## Enum: ProtocolEnum

| Name | Value |
|---- | -----|
| HTTPS | &quot;https&quot; |



