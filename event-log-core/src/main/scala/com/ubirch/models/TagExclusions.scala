package com.ubirch.models

trait TagExclusions {

  def `tags-exclude:`: String = "tags-exclude:"

  def headerExcludeBlockChain: (String, String) = HeaderNames.DISPATCHER -> (`tags-exclude:` + ":blockchain")
  def headerExcludeFoundation: (String, String) = HeaderNames.DISPATCHER -> (`tags-exclude:` + "foundation")
  def headerExcludeAggregation: (String, String) = HeaderNames.DISPATCHER -> (`tags-exclude:` + "aggregation")
  def headerExcludeStorage: (String, String) = HeaderNames.DISPATCHER -> (`tags-exclude:` + "storage")

}
