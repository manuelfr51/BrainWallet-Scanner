import java.util.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.bitcoinj.core.*;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.params.MainNetParams;

class BrainwScanner {

  private final static int barLength = 23;  /* Length of progress bar. */

  static int numThreads = 1;                /* Number of threads to run. */
  static Thread[] threads;                  /* Threads that query blockexplorer. */

  /* Start and end indexes for a thread 
   * to work on */
  static int threadWorkStart;
  static int threadWorkEnd;                 

  /* Holds all wallets found with an amount. */
  static List<BtcWallet> keys = new ArrayList<BtcWallet>();
  /* Holds all wallets where query to blockexplorer failed. */
  static List<String> failedPhrases = new ArrayList<String>();

  /* Array of all brainwallet phrases from input. */
  static String[] phrases;

  public static void main(String[] args) {

    if (args.length < 1) {
      System.err.println("usage: BrainwScanner [FILE] ...");
      System.exit(0);
    }
    
    /* Parses brainwallet phrases into a set to avoid
     * duplicates. */
    Set<String> phrasesSet = readFilesToSet(args);
    phrases = phrasesSet.toArray(new String[0]);

    System.out.println("\nChecking for non-empty bitcoin wallets");
    System.out.printf("Phrases to check: %d\n", phrases.length);

    numThreads = (int) (phrases.length * 0.3); /* factor of .3 was found
                                                  to run pretty fast on
                                                  a phrase set of 77 */

    /* 
     * Why the limit?
     *
     * Originally when I didn't have a limit and ran the script with over 25143
     * lines of input, blockexplorer restricted my access due to their server overload
     * protection. Lasted only about 20 minutes, but this new limit was imposed to keep
     * things safe.
     */
    if (numThreads > 25) {
      numThreads = 25;
    } else if (numThreads < 1) {
      numThreads = 1;
    }

    /* Start thread work and wait. */
    spawnThreads(numThreads);
    waitForThreads();

    if (!failedPhrases.isEmpty()) {
      recordFailedQueries();
    }
    
    /* Print out the results,
     * any object that was added to keys. */
    System.out.printf("\nRESULTS: %d matches!\n\n", keys.size());
    Iterator<BtcWallet> iter = keys.iterator();

    BtcWallet curWal;
    while(iter.hasNext()) {
      curWal = iter.next();
      System.out.println(curWal.toString() + "\n");
    }

  }

  static void spawnThreads(int numThreads) {

    /* Split used for spliting number of phrases
     * to work on in a single thread. */
    int split = phrases.length / numThreads;
    threadWorkStart = 0;
    threadWorkEnd   = split;

    /* Print initial progress while nothing is running. */
    printProgress(0);
    printFilesPerSec(0);

    /* Create and start all threads. */
    threads = new Thread[numThreads];
    for (int i = 0; i < numThreads; i++) {

      /* Have final thread do any leftover work. */
      if (i == numThreads-1) {
        threadWorkEnd += (phrases.length - threadWorkEnd - 1);
      }

      /* Adds a new thread to threads[] and gets it started. */
      threads[i] = 

      new Thread("Thread " + (i+1)) {

        int start = threadWorkStart;
        int end = threadWorkEnd;

        public void run() {
          doThreadWork(start, end);
        }

      };
      threads[i].start();

      threadWorkStart = threadWorkEnd+1;
      threadWorkEnd += split;

    }

  }

  static void waitForThreads() {

    /* Wait for all threads to complete. */
    try {

      for (int i = 0; i < threads.length; i++) {
        threads[i].join();
      }


    } catch (Exception e) {
      System.err.println("Exception: " + e);
    }

    /* Done work, print progress. */
    clearFilesPerSec();
    clearProgress();
    printProgress(100);

  }

  /* Save unchecked phrases to output file. */
  static void recordFailedQueries() {

    System.out.println("\nWARNING: Some phrases left unchecked.\n" +
                       "Unchecked phrases saved in output_files/failed.txt\n");

    BufferedWriter writer = null;
    try {

      File file = new File("output_files/failed.txt");
      writer = new BufferedWriter(new FileWriter(file));

      String[] toFailed = failedPhrases.toArray(new String[0]);

      for (int i = 0; i < toFailed.length; i++) {
        writer.write(toFailed[i] + "\n");
      }


    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {writer.close();} catch (Exception ex) {/* ignore */}
    }

  }



  
  /* Values for printing out 
   * progress bar. */
  static int percent = 0;
  static int oldP = 0;
  static int percCounter = 0;

  /* Values for tracking connections per second */
  static int seconds = (int) (System.currentTimeMillis() / 1000);
  static int perSecCount = 0;
  static int perSecAvg = 0;

  /* Lock for printing to stdout */
  static Lock progressLock = new ReentrantLock();

  /*
   * This function iterates over phrases from passed
   * indices and converts the phrase to a private wallet,
   * private to public, and checks the amount on the public
   * address.
   */
  static void doThreadWork(int start, int end) {

    String privateKey = "";
    String publicKey = "";
    byte[] pKeyHash;
    double amount = 0;;

    /* Iterate through each phrase, get wallet deets,
     * and check if an amount existed. */
    for (int i = start; i <= end; i++) {

      perSecCount++;
      percCounter++;

      /* Acquire a lock for progress printing. */
      progressLock.lock();
      int curTime = (int) (System.currentTimeMillis() / 1000);
      if (curTime > seconds) {
        seconds = curTime;
        perSecAvg += perSecCount;
        if (perSecAvg != perSecCount) {
          perSecAvg = perSecAvg / 2;
        }
        clearFilesPerSec();
        printFilesPerSec(perSecAvg);
        perSecCount = 0;

      }


      percent = (int) ( ((double) percCounter/phrases.length) * 100 );

      if (percent != oldP) {
        clearFilesPerSec();
        clearProgress();
        printProgress(percent);
        printFilesPerSec(perSecAvg);

        oldP = percent;
      }
      progressLock.unlock();

      pKeyHash = sha256(phrases[i]);
      privateKey = privToString(pKeyHash);

      publicKey = getPublicKey(pKeyHash);

      if (publicKey != null) {
        amount = getAmount(publicKey);
      } else {
        amount = 0;
      }

      if (amount < 0) {
        failedPhrases.add(phrases[i]);
      } else if (amount > 0) {
        keys.add(new BtcWallet(privateKey, publicKey, amount, phrases[i]));
      }
    }
  }

  static int len = 0;
  /*
   * Prints fps
   */
  static void printFilesPerSec(int count) {

    String out = String.format("  @ %3d connections/sec", count);
    len = out.length();
    System.out.printf("%s", out);

  }

  /*
   * Clears the fps printed of length len
   */
  static void clearFilesPerSec() {

    for (int i = 0; i < len; i++) {
      System.out.printf("\b");
    }

  }

  /*
   * Prints out progress bar to stdout
   * according to the current progress.
   */
  static void printProgress(int prog) {

    System.out.printf("[");

    double perc = prog / 100.0;
    int ratio = (int) (perc * barLength);

    int i;
    for (i = 0; i < ratio; i++) {
      System.out.printf("#");
    }
    for (; i < barLength; i++) {
      System.out.printf("-");
    } 

    System.out.printf("]%4d%%", prog);
    System.out.flush();

  }

  /*
   * Prints out the required number of backspaces
   * so that the progress bar can be reprinted
   *
   * The magic number 7 accounts for:
   * spacing of percent number plus percent sign character
   * plus 2 square brackets.
   */
  static void clearProgress() {
    
    for (int i = 0; i < barLength + 7; i++) {
      System.out.printf("\b");
    }
    System.out.flush();

  }

  /*
   * Reads files passed on command line and parses through
   * to gather phrases.
   *
   * Stores the phrases in HashSet to avoid duplicates.
   */
  static Set<String> readFilesToSet(String[] files) {

    Set<String> phraseSet = new HashSet<String>();

    /* Iterate through each arg file. */
    for (int i = 0; i < files.length; i++) {

      try {

        String fName = files[i];

        /* Add next word to set */
        Scanner file = new Scanner(new File(fName));
        while (file.hasNext()) {
          phraseSet.add(file.next());
        }
        file.close();
        
        /* Do the same, but with each line. */
        file = new Scanner(new File(fName));
        String str = "";
        while(file.hasNextLine()) {
          str = file.nextLine().trim();

          /* Remove first word if it starts with a number.
           * Usually that means line numbering in poems. */
          if (str.length() > 1 && str.substring(0,1).matches("\\d")) {
            phraseSet.add(str.substring(str.indexOf(" ")+1));
          } else if (str.length() > 1 && 
              str.substring(str.length()-1, str.length()).matches("\\d")) {
            phraseSet.add(str.substring(0, str.lastIndexOf(" ")));
          } else {
            phraseSet.add(str);
          }
        }
        file.close();

      } catch(Exception e) {
        System.err.printf("Error reading in file %s\n", files[i]);
      }

    }
    return phraseSet;

  }


  private static final int InitialSleep = 100;
  private static final int RestrictedSleep = 1000;
  static int sleepFor = InitialSleep;  /* For limiting connection speed. */
  /* 
   * Checks online to see if the wallet at pubKey
   * has had an amount or is empty.
   */
  static double getAmount(String pubKey) {

    double amount = 0;
    try {
      URLConnection uc;
      //URL url = new URL("http://blockexplorer.com/q/addressbalance/"+pubKey);
      URL url = new URL("http://blockexplorer.com/q/getreceivedbyaddress/"+pubKey);
      Thread.sleep(sleepFor);
      uc = url.openConnection();
			uc.addRequestProperty("User-Agent", "Mozilla/4.0 (compatible); MSIE 6.0; Windows NT 5.0)");
			uc.connect();
			uc.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			String line = br.readLine();
			amount = Double.parseDouble(line);

      /* If we reach this line, no exception occurred,
       * so wait 100 ms between query again. */
      sleepFor = InitialSleep;

		} catch (Exception e) {
      System.err.println("Exception: " + e);
      sleepFor = RestrictedSleep;
      amount = -1;  /* So -1 is returned and we know we failed. */
    }

		return amount;

  }

  /*
   * Uses bitcoinj libraries to get the public key
   * as a string from the passed private key byte.
   */
  static String getPublicKey(byte[] privKey) {

    Address address = new Address(MainNetParams.get(), 
        Utils.sha256hash160(ECKey.fromPrivate(privKey, false).getPubKey()));

    return address.toString();

  }

  /*
   * Returns the sha256 sum, the private key, of a string.
   *
	 * http://stackoverflow.com/questions/5531455/how-to-encode-some-string-with-sha256-in-java
   */
	static byte[] sha256(String base) {
	  try{
	      MessageDigest digest = MessageDigest.getInstance("SHA-256");
	      byte[] hash = digest.digest(base.getBytes("UTF-8"));
        return hash;
	  } catch(Exception ex){
      throw new RuntimeException(ex);
	  }
	}

  /* 
   * Converts the private key byte value to a human readable
   * string
   */
  static String privToString(byte[] hash) {

	  StringBuffer hexString = new StringBuffer();

	  for (int i = 0; i < hash.length; i++) {
	      String hex = Integer.toHexString(0xff & hash[i]);
	      if(hex.length() == 1) hexString.append('0');
	      hexString.append(hex);
	  }

	  return hexString.toString();

  }



  /* Container class for wallet vals. */
  private static class BtcWallet {

    private String pKey;
    private String addr;
    private double amt;
    private String phrase;

    BtcWallet(String pKey, String addr, double amt, String phrase) {
      this.pKey = pKey;
      this.addr = addr;
      this.amt = amt;
      this.phrase = phrase;
    }

    /*
     * Formatted for output.
     */
    public String toString() {
      return "Phrase: " + phrase + "\nPrivate Key: " + pKey 
        + "\nPublic Key: " + addr + "\nAmount: " + amt + "";
    }

  }

}
