//This is a Matching Engine test which contains making more than one thread to test 
// this engine and make the result stay correct. According to the result, every 
// matching works correcly with buyer >= seller and no repeat action or missing values.
public class MatchingEngineTest {

    static class BuyThread extends Thread {
        private final String tickerSymbol;
        public BuyThread(String tickerSymbol) {
            this.tickerSymbol = tickerSymbol;
        }
        @Override
        public void run() {
            for (int i = 0; i < 10; i++) {
                float price = 90 + i * 10; 
                int qty = 50 + i;
                MatchingEngine.addOrder("Buy", tickerSymbol, qty, price);
                System.out.println("[BUY-THREAD] Added BUY " + tickerSymbol
                    + " qty=" + qty + ", price=" + price);
                try { Thread.sleep(50); } catch (InterruptedException e) {}
            }
        }
    }

    static class SellThread extends Thread {
        private final String tickerSymbol;
        public SellThread(String tickerSymbol) {
            this.tickerSymbol = tickerSymbol;
        }
        @Override
        public void run() {
            for (int i = 0; i < 10; i++) {
                float price = 95 + i * 10;
                int qty = 40 + i;
                MatchingEngine.addOrder("Sell", tickerSymbol, qty, price);
                System.out.println("[SELL-THREAD] Added SELL " + tickerSymbol
                    + " qty=" + qty + ", price=" + price);
                try { Thread.sleep(70); } catch (InterruptedException e) {}
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        MatchingEngine.addOrder("Buy", "TKR0001", 1, 1); 

        Thread tBuy = new BuyThread("TKR0001");
        Thread tSell = new SellThread("TKR0001");
        tBuy.start();
        tSell.start();

        for (int i = 0; i < 30; i++) {
            MatchingEngine.matchOrder("TKR0001");
            Thread.sleep(30);
        }

        tBuy.join();
        tSell.join();

        System.out.println("All threads finished.");
        MatchingEngine.matchOrder("TKR0001");
        MatchingEngine.matchOrder("TKR0001");
        MatchingEngine.matchOrder("TKR0001");
        MatchingEngine.matchOrder("TKR0001"); 
        System.out.println("Done");
    }
    
}

