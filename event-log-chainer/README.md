# UBIRCH EVENT CHAINER

The _Event Log Chainer_ is a service able to create a Merkle Tree from hashed EventLogs (payload only) that belong to different customers and whose hash roots are eventually anchored to public blockchains.

It comes in two flavors or modes, Slave or Foundation and Master. Depending on this mode, the Chainer will chain raw packages or trees.

![Event Log Chainer](https://raw.githubusercontent.com/ubirch/ubirch-event-log/master/.images/tree_creation_workflow.png "Event Log Chainer")
