///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS com.sparkjava:spark-core:2.9.1
//DEPS com.fasterxml.jackson.core:jackson-databind:2.10.2
//DEPS com.twilio.sdk:twilio:7.54.2
//SOURCES Disease.java

import com.twilio.twiml.MessagingResponse;
import com.twilio.twiml.messaging.Body;
import com.twilio.twiml.messaging.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;

/**
 * This Spark web server provides an /sms endpoint to receive Twilio webhook callbacks and
 * decode the codified message, replying to the SMS with the decoded data in a text
*/
public class ReportingServer {

    static int[] primes = new int[]{2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31};

    public static void main(String args[]) {
        get("/", (req, res) -> "Health Check");

        post("/sms", (req, res) -> {
            String responseText = null;
            try {
                String[] webhookParts = req.body().split("&");
                for (String webhookPart : webhookParts) {
                    if (webhookPart.startsWith("Body=")) {
                        String body = webhookPart.split("=")[1];
                        DecodedMessage msg = decodeMessage(body);
                        if (msg != null) {
                            responseText = msg.toString();
                        } else {
                            responseText = "Oops! Your message does not appear to be valid.";
                        }
                        res.type("application/xml");
                        break;
                    }
                }
            } catch (Exception e) {
                // do nothing for now
            }

            if (responseText == null) {
                responseText = "An error was encountered";
            }

            Body body = new Body.Builder(responseText).build();
            Message sms = new Message.Builder().body(body).build();
            MessagingResponse twiml = new MessagingResponse.Builder().message(sms).build();
            return twiml.toXml();
        });
    }

    /**
     * Messages are encoded by first selecting a random prime number < 32 for the day-of-month seed,
     * and then using the next two primes for the disease and number-of-cases seeds. That means that
     * if you can identify the prime number used for the day-of-month 3-digit code, you know what
     * are the possible valid codes for the other values provided too, and the entire message can be
     * both decoded and validated (since a typo in a code provided by a user will result in a value
     * being provided that doesn't adhere to the sequential-prime-seed rule)
     */
    static DecodedMessage decodeMessage(String codifiedMessage) {
        // these data structures will be used for efficient lookups to reverse engineer
        // the codified message to the original day-of-month, disease, and number-of-cases data
        // values that the reporting user originally chose.
        // It does this by attempting to figure which prime number was used as the day-of-month seed
        // and then validates the user's message by confirming that the respective 3-digit codes
        // for the disease and number-of-cases are legal values

        // key = 3 digit code for each day of month (1-31), value = map(key=index in primes
        // array, value=multiplier)
        Map<String, List<Integer>> primeIndexesByDayCode = new HashMap<>();
        // key = index in primes array, value = list(valid disease codes for equivalent prime)
        Map<Integer, List<String>> diseaseCodesByPrimeIndex = new HashMap<>();
        // key = index in primes array, value = list(valid cases codes for equivalent prime)
        Map<Integer, List<String>> casesCodesByPrimeIndex = new HashMap<>();

        // for each permissible prime number, populate the above data structures
        for (int primeIndex = 0; primeIndex < primes.length; primeIndex++) {
            // the day codes will only use the 1st to the third-last prime indexes for 9-digit codes
            if (primeIndex < (primes.length - 2)) {
                // for each day of the month, build the lookup cache for primes by legal 3-digit
                // day-of-month codes
                primeIndexesByDayCode = buildPrimeIndexLookupsByDayCode(primeIndexesByDayCode,
                        primeIndex);
            }

            // the disease codes will only use the 2nd to the second-last prime indexes for 9-digits
            if (primeIndex > 0 && (primeIndex < (primes.length - 1))) {
                // for each prime, build a cache of legal 3-digit disease codes
                diseaseCodesByPrimeIndex =
                        buildDiseaseCodeLookupByPrimeIndex(diseaseCodesByPrimeIndex,
                                primeIndex);
            }

            // the cases codes will only use the 3rd to the last prime indexes for 9-digit codes
            if (primeIndex > 1) {
                // for each prime, build a cache of legal 3-digit number-of-cases codes
                casesCodesByPrimeIndex = buildPrimeLookupByCasesCode(casesCodesByPrimeIndex,
                        primeIndex);
            }
        }

        // the first 3 digits correspond the codified day of the month
        String impliedDayCode = codifiedMessage.substring(0, 3);

        // check if the codified message is valid by checking if each of the day-of-month,
        // disease, and number-of-cases 3-digit codes are legal; if they all are, then the message
        // is valid and we can reverse engineer the user's original selections
        if (primeIndexesByDayCode.containsKey(impliedDayCode)) {
            // the primes that are legal for this day-of-month code
            List<Integer> primeIndexesForDayCode = primeIndexesByDayCode.get(impliedDayCode);
            for (Integer primeIndex : primeIndexesForDayCode) {
                // now check if the disease code is legal
                if (diseaseCodesByPrimeIndex.containsKey(primeIndex + 1)) {
                    // get the legal disease codes for the next sequential prime
                    List<String> diseaseCodeIndexesByCode =
                            diseaseCodesByPrimeIndex.get(primeIndex + 1);
                    String impliedDiseaseCode = codifiedMessage.substring(3, 6);
                    if (diseaseCodeIndexesByCode.contains(impliedDiseaseCode)) {
                        // finally check if the number-of-cases code is legal
                        if (casesCodesByPrimeIndex.containsKey(primeIndex + 2)) {
                            List<String> casesCodeIndexesByCode =
                                    casesCodesByPrimeIndex.get(primeIndex + 2);
                            String impliedCasesCode = codifiedMessage.substring(6);
                            if (casesCodeIndexesByCode.contains(impliedCasesCode)) {
                                // if we have reached this point, then all 3-digit codes are
                                // legal and therefore the message is legal and valid. We also know
                                // now what prime number seeds were used by the original user's
                                // reporting wheel and therefore can figure out what the original
                                // non-encoded values were for day-of-month, disease, and
                                // number-of-cases
                                int dayOfMonth =
                                        Integer.valueOf(impliedDayCode) / primes[primeIndex];
                                Disease disease =
                                        Disease.values()[diseaseCodeIndexesByCode.indexOf(impliedDiseaseCode)];
                                int numberOfCases =
                                        casesCodeIndexesByCode.indexOf(impliedCasesCode) + 1;
                                return new DecodedMessage(dayOfMonth, disease, numberOfCases);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * For each day of the month, generate a 3-digit-code and note which prime seeds would result in
     * that code being a legal value
     */
    static Map<String, List<Integer>> buildPrimeIndexLookupsByDayCode(
            Map<String, List<Integer>> dayCodes, int primeIndex) {
        for (int dayIndex = 1; dayIndex < 32; dayIndex++) {
            String dayCode = String.format(Locale.ROOT, "%03d", (dayIndex * primes[primeIndex]));
            if (dayCodes.containsKey(dayCode)) {
                dayCodes.get(dayCode).add(primeIndex);
            } else {
                List<Integer> primeIndexesForDayCode = new ArrayList<>();
                primeIndexesForDayCode.add(primeIndex);
                dayCodes.put(dayCode, primeIndexesForDayCode);
            }
        }
        return dayCodes;
    }

    // as above
    static Map<Integer, List<String>> buildDiseaseCodeLookupByPrimeIndex(
            Map<Integer, List<String>> diseaseCodesByPrime, int primeIndex) {
        for (Disease dis : Disease.values()) {
            String diseaseCode = String.format(Locale.ROOT, "%03d",
                    ((dis.ordinal() + 1) * primes[primeIndex]));

            if (diseaseCodesByPrime.containsKey(primeIndex)) {
                diseaseCodesByPrime.get(primeIndex).add(diseaseCode);
            } else {
                List<String> diseaseCodes = new ArrayList<>();
                diseaseCodes.add(diseaseCode);
                diseaseCodesByPrime.put(primeIndex, diseaseCodes);
            }
        }
        return diseaseCodesByPrime;
    }

    // and as above also
    static Map<Integer, List<String>> buildPrimeLookupByCasesCode(
            Map<Integer, List<String>> casesCodesByPrime, int primeIndex) {
        for (int casesIndex = 1; casesIndex < 21; casesIndex++) {
            String casesCode = String.format(Locale.ROOT, "%03d",
                    (casesIndex * primes[primeIndex]));
            if (casesCodesByPrime.containsKey(primeIndex)) {
                casesCodesByPrime.get(primeIndex).add(casesCode);
            } else {
                List<String> casesCodes = new ArrayList<>();
                casesCodes.add(casesCode);
                casesCodesByPrime.put(primeIndex, casesCodes);
            }
        }
        return casesCodesByPrime;
    }

    static class DecodedMessage {
        final int dayOfMonth;
        final Disease disease;
        final int numberOfCases;

        DecodedMessage(int dayOfMonth, Disease disease, int numberOfCases) {
            this.dayOfMonth = dayOfMonth;
            this.disease = disease;
            this.numberOfCases = numberOfCases;
        }

        public int getDayOfMonth() {
            return dayOfMonth;
        }

        public Disease getDisease() {
            return disease;
        }

        public int getNumberOfCases() {
            return numberOfCases;
        }

        @Override
        public String toString() {
            return String.format("Decoded Message:%n" +
                    "dayOfMonth=%d%n" +
                    "disease=%s%n" +
                    "numberOfCases=%d%n" +
                    "%nThank you for your report!", dayOfMonth, disease, numberOfCases);
        }
    }

}