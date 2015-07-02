Brain Wallet Scanner Project
============================


Building
--------

  A simple Makefile and bash script are included for running the program under a
 * nix system.

How it Works
------------

  The program uses Java's built in sha256 function to convert parsed phrases
from a specified text file to a private bitcoin wallet. Using the bitcoinj
libraries, the public addresses from each private address are found. Then a
search on blockexplorer.com's public api is performed to get the total amount
received in that wallet's history.  
  These queried results are then printed for the user to see on stdout, showing
the phrase, private address, public address, and total amount received.  

Findings
--------

  Through running the program over various text files from Gutenberg, the
results were mostly the same. It appears that very common words often have a
transaction history of a very very small amount of bitcoins.  
  Viewing one of these addresses on the blockchain reveals that they transfered
this amount quickly to another account. If you experiment with the blockchain,
you can even find that these accounts have had numerous sends to the same
account, which then sends the same fractional amount across various other
bitcoin addresses.  
  My conclusion is that this is the result of some various bitcoin tumblers
which you can often find info about online. These tumblers work similarly to
money laundering services with physical currency. I believe that what I've found
is the result of one or more of these tumblers running a similar program which
gathers wallets from common words and then conducts small transactions to hide
the original source of these bitcoins. This is all in an attempt to further
anonymize where one acquired bitcoins from.
