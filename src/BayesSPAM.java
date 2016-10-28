import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;

import javax.swing.*;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Stream;

/**
 * Created by Seb on 12.10.2016.
 */
public class BayesSPAM {
    // Folder names as strings. Make sure those folders contain the mail-files. (NO ZIP FILES!)
    private static final String HAM_ANLERN = "/ham-anlern";
    private static final String SPAM_ANLERN = "/spam-anlern";
    private static final String HAM_CALIBRATE = "/ham-kallibrierung";
    private static final String SPAM_CALIBRATE = "/spam-kallibrierung";
    private static final String HAM_REAL_TEST = "/ham-test";
    private static final String SPAM_REAL_TEST = "/spam-test";
    Map<String, Double> hamMap = new HashMap<>();
    Map<String, Double> spamMap = new HashMap<>();
    Map<String, Double> tmpMap = new HashMap<>();
    private double hamCounter = 0; // counts how many ham mails have been scanned in order to learn
    private double spamCounter = 0; // same as above but with spam mails
    private final double SMALL_VALUE_FIX = 0.1; // works the best at 0.1
    // the following two must result in 1 when added
    private final double HAM_ASSUMPTION = 0.5;
    private final double SPAM_ASSUMPTION = 0.5;
    // lambda does not allow var to be used. Using array later on instead...
    //private final double[] NUMERATOR = new double[1]; not used anymore
    private final double[] DENOMINATOR = new double[1];

    public static void main (String[] args) {
        BayesSPAM spamFilter = new BayesSPAM();
        spamFilter.learnHam();
        spamFilter.learnSpam();
        spamFilter.fixMaps();
        // the following two methods can be used for manual calibration
        //spamFilter.helpCalibrate(HAM_CALIBRATE);
        //spamFilter.helpCalibrate(SPAM_CALIBRATE);
        //spamFilter.testSpamFilter();
        spamFilter.readMailLoop();
    }

    /***
     * Generates hamMap of files in /resources/ham-anlern
     */
    public void learnHam(){
        try{
            URL resource = getClass().getResource(HAM_ANLERN);
            File sourceFolder = Paths.get(resource.toURI()).toFile();
            for (File file : sourceFolder.listFiles()) {
                analyzeMail(file);
                transferWordsFromTmpIntoOtherMap(true); // working in ham context here
                hamCounter++; // we've worked on one more ham mail
            }
        } catch (Exception e){
            System.out.println("Folder '"+HAM_ANLERN+"' is probably not located in the resources folder of the project!");
        }
    }

    /***
     * Generates spamMap of files in /resources/spam-anlern
     */
    public void learnSpam(){
        try{
            URL resource = getClass().getResource(SPAM_ANLERN);
            File sourceFolder = Paths.get(resource.toURI()).toFile();
            for (File file : sourceFolder.listFiles()) {
                analyzeMail(file);
                transferWordsFromTmpIntoOtherMap(false); // working in spam context here
                spamCounter++;
            }
        } catch (Exception e){
            // doesn't matter
        }
    }

    /**
     * Fixes appearances of words that are in one map but not in the other.
     */
    public void fixMaps(){
        hamMap.forEach((key, aDouble) -> {
            if(!spamMap.containsKey(key)){
                spamMap.put(key, SMALL_VALUE_FIX);
            }
        });
        spamMap.forEach((key, aDouble) -> {
            if(!hamMap.containsKey(key)){
                hamMap.put(key, SMALL_VALUE_FIX);
            }
        });
    }

    /**
     * Transfers words from tmpMap and adds them into the correct Map increasing the numbers.
     * @param ham if true, then we are dealing with a ham-words. False = spam-words.
     */
    public void transferWordsFromTmpIntoOtherMap(boolean ham){
        if(ham){
            tmpMap.forEach((key, aDouble) -> {
                if(hamMap.containsKey(key)){
                    hamMap.put(key, hamMap.get(key)+1);
                } else {
                    hamMap.put(key, 1.0);
                }
            });
        } else {
            tmpMap.forEach((key, aDouble) -> {
                if(spamMap.containsKey(key)){
                    spamMap.put(key, spamMap.get(key)+1);
                } else {
                    spamMap.put(key, 1.0);
                }
            });
        }
        tmpMap.clear();
    }

    /***
     * Maps all words of a mail into tmpMap.
     * @param mail the mail as a file
     */
    public void analyzeMail(File mail){
        try{
            BufferedReader br = new BufferedReader(new FileReader(mail));
            Stream<String> fileStream = br.lines();
            fileStream.forEach(line -> analyzeLine(line));
            br.close();
        } catch(Exception e){
            System.out.println("Mail could not be read.");
        }
    }

    /***
     * Gets a line and adds each word in it to tmpMap with key value 1.
     * @param line line being looked at
     */
    public void analyzeLine(String line){
        String[] parts = line.split(" ");
        //String pimpedLine = line.replaceAll("[^a-zA-Z\\s]",""); // [a-zA-z\\s] would replace alphabetic sings and space. The "^" negates the regex.
        //String[] parts = pimpedLine.split(" ");
        String tmp = "";
        for(int i = 0; i < parts.length; i++){
            tmp = parts[i].toLowerCase();
            tmpMap.put(tmp, 1.0);
            /*
            It does not matter how often a word shows up. In this case we are working in the same mail, thus the key value is always 1.
             */
        }

    }

    /**
     * Gets a mail and tells the probability of it being spam.
     * @param mail mail being looked at.
     * @return e.g. 0.1 = 10% chance it is SPAM
     */
    public double calculateSPAMprobability(File mail){
        tmpMap.clear(); // make sure it's clear
        analyzeMail(mail); // tmpMap now has all words that appeared in this mail
        DENOMINATOR[0] = 1; // part of the "Nenner" in our case
        // check words that appear in all 3 maps and use those, building the number one factor at a time to avoid NaN or Infinity.
        tmpMap.forEach((key, aDouble1) -> {
            if(hamMap.containsKey(key) && spamMap.containsKey(key)){
                DENOMINATOR[0] = DENOMINATOR[0] * (hamMap.get(key)*spamCounter)/(hamCounter*spamMap.get(key));
            }
        });
        /*
        the following line make the final calculation,
        considering that we would multiply by HAM_ASSUMPTION (e.g. 1/2=0.5) and
        divide by SPAM_ASSUMPTION (e.g. 1/2=0.5). Note that it's a division because of changing the equation.
         */
        DENOMINATOR[0] = DENOMINATOR[0]*HAM_ASSUMPTION;
        DENOMINATOR[0] = DENOMINATOR[0] / SPAM_ASSUMPTION;
        return 1/(1 + DENOMINATOR[0]); // final equation
    }

    /***
     * Scans mails in a given folder and prints results of spam test.
     * @param folder folder being looked at.
     */
    public void helpCalibrate(String folder){
        System.out.println("CALIBRATING: " + folder);
        try{
            URL resource = getClass().getResource(folder);
            File sourceFolder = Paths.get(resource.toURI()).toFile();
            for(File file : sourceFolder.listFiles()){
                System.out.println(calculateSPAMprobability(file));
                // For HAM -> most of results should be 0.0(?)
                // For SPAM -> most of results should be 1.0
            }
        } catch (Exception e){
            System.out.println("Mails not found in '"+folder+"'!");
        }
    }

    /***
     * Scans the real ham and spam folders and prints out statistics.
     */
    public void testSpamFilter(){
        int totalSpamMails = 0;
        int seenAsSpam = 0;
        int totalHamMails = 0;
        int seenHamAsSpam = 0;
        try{
            URL resource = getClass().getResource(SPAM_REAL_TEST);
            File spamFolder = Paths.get(resource.toURI()).toFile();
            totalSpamMails = spamFolder.listFiles().length;

            resource = getClass().getResource(HAM_REAL_TEST);
            File hamFolder = Paths.get(resource.toURI()).toFile();
            totalHamMails = hamFolder.listFiles().length;

            /*
            In the following lines we assume that the SPAMFilter
            is only then working correctly, when SPAM is 100% and HAM 0% detected as SPAM.
             */
            for(File spamMail : spamFolder.listFiles()){
                if(calculateSPAMprobability(spamMail) == 1.0){
                    seenAsSpam++; // Spam detected
                }
            }
            for(File hamMail : hamFolder.listFiles()){
                if(calculateSPAMprobability(hamMail) == 0.0){
                    seenHamAsSpam++;
                }
            }
            System.out.println("HAM Mails, wrongly seen as SPAM: " + seenHamAsSpam + "/" + totalHamMails + " ("+(seenHamAsSpam*100/totalHamMails)+"%)");
            System.out.println("SPAM Mails, correctly seen as SPAM: " + seenAsSpam + "/" + totalSpamMails + " ("+(seenAsSpam*100/totalSpamMails)+"%)");
        } catch (Exception e){
            System.out.println(SPAM_REAL_TEST + " folder and " + HAM_REAL_TEST + " folder not found!");
        }
    }

    /***
     * Allows user to select a file, scan it for spam and adjust statistics if needed.
     */
    public void readMail(){
        JFileChooser fileChooser = new JFileChooser();
        // at this point the OpenDialog may not show up as first window. Other windows in the system have to be checked.
        if(fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION){
            // user selected a file and clicked "Open"
            File tmpFile = fileChooser.getSelectedFile();
            double spamProbability = calculateSPAMprobability(tmpFile);
            System.out.println("P(Mail chosen is SPAM) = " + spamProbability);
            System.out.print("Therefore mail is... ");
            if(spamProbability >= 0.5){
                System.out.print("probably SPAM!");
            } else {
                System.out.print("probably not SPAM!");
            }
            System.out.println("");
            System.out.println("Is mail SPAM or HAM? Please answer with SPAM='1' or HAM='0': ");
            Scanner userInput = new Scanner(System.in);
            int answer = userInput.nextInt();
            if(answer == 0){
                updateStatsWithMail(tmpFile, true); // ham context here
                return;
            }
            if(answer == 1){
                updateStatsWithMail(tmpFile, false);
                return;
            }
            System.out.println("Wrong answer...");
        }
    }

    /***
     * Updates the SPAM-Filters statistics with a mail accordingly to its context.
     * @param mail file being looked at.
     * @param ham true in case it's a ham mail, false if spam.
     */
    public void updateStatsWithMail(File mail, boolean ham){
        analyzeMail(mail);
        transferWordsFromTmpIntoOtherMap(ham);
        if(ham){
            hamCounter++;
        } else{
            spamCounter++;
        }
        fixMaps();
        System.out.println("Statistics updated...");
    }

    /**
     * Creates a loop which allows user to read many mails and make SPAM-filter learn or quit.
     */
    public void readMailLoop(){
        Scanner userInput = new Scanner(System.in);
        int answer = 1;
        while(answer != 0){
            System.out.println("Please select an option: ");
            System.out.println("1: Read a mail and learn");
            System.out.println("2: test all SPAM / HAM Mails");
            System.out.println("0 or else: quit");
            answer = userInput.nextInt();
            switch (answer){
                case 1:
                    readMail();
                    break;
                case 2:
                    testSpamFilter();
                    break;
                default:
                    System.out.println("Exiting...");
                    answer = 0;
                    break;
            }
        }
    }
}
