package com.ubirch.encoder.models

/***
 * Represents a data object that has been generated in the blockchain service.
 *
 * {
 *  "status":"added",
 *  "txid":"51f6cfe400bd1062f8fcde5dc5c23aaac111e8124886ecf1f60c33015a35ccb0",
 *  "message":"e392457bdd63db37d00435bfdc0a0a7f4a85f3664b9439956a4f4f2310fd934df85ea4a02823d4674c891f224bcab8c8f2c117fdc8710ce78c928fc9de8d9e19",
 *  "blockchain":"ethereum",
 *  "network_info":"Rinkeby Testnet Network",
 *  "network_type":"testnet",
 *  "created":"2019-05-07T21:30:14.421095"
 * }
 *
 * @param status Represents the status of the request
 * @param txid Represents the transaction id for the request
 * @param message Represents the message
 * @param blockchain Represents which blockchain it is
 * @param networkInfo Represents a description for the blockchain
 * @param networkType Represents a type of blockchain. Testnet/Mainet
 * @param created Represents a data for when the transaction was created.
 */
case class BlockchainResponse(status: String, txid: String, message: String, blockchain: String, networkInfo: String, networkType: String, created: String) {
  val category: String = (blockchain + "_" + networkType + "_" + networkInfo)
    .replace(" ", "_")
    .toUpperCase()
}
