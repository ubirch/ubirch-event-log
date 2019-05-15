# UBIRCH EVENT CHAINER

The Event Log Chainer is a service that is able to create a merkle tree from hashed eventlogs (payload only) that belong to different customers and whose hash roots are eventually anchored to public blockchains.

It comes in two flavors or modes, slave and master. Depending on this mode, the chainer will chain raw packages or trees.