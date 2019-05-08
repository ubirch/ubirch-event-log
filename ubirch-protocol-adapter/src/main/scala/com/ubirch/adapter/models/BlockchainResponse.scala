package com.ubirch.adapter.models

/**
  * {
  * "status": "added",
  * "txid": "51f6cfe400bd1062f8fcde5dc5c23aaac111e8124886ecf1f60c33015a35ccb0",
  * "message": "e392457bdd63db37d00435bfdc0a0a7f4a85f3664b9439956a4f4f2310fd934df85ea4a02823d4674c891f224bcab8c8f2c117fdc8710ce78c928fc9de8d9e19",
  * "blockchain": "ethereum",
  * "network_info": "Rinkeby Testnet Network",
  * "network_type": "testnet",
  * "created": "2019-05-07T21:30:14.421095"
  * }
  */
case class BlockchainResponse(status: String, txid: String, message: String, blockchain: String, networkInfo: String, networkType: String, created: String) {
  val category = (blockchain + "_" + networkType + "_" + networkInfo).replace(" ", "_").toUpperCase()
}
