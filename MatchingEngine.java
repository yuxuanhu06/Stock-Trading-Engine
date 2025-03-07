public class MatchingEngine {
    private static final int MAX_TICKERS = 1024;
    private static final int MAX_ORDERS_PER_SIDE = 10000;
    /**
     * Storing 1024 ticker
     */
    private static final String[] tickerSymbols = new String[MAX_TICKERS];

    /**
     * I asked LLM and do some research online that which said maybe using volatile, atomic
     * boolean and int could getting closer with the lock free situation without import 
     * anything else.
     */
    private static final class Order {
        final java.util.concurrent.atomic.AtomicBoolean active = 
            new java.util.concurrent.atomic.AtomicBoolean(false); 
            // True == still valid
            // By using atomicboolean, I am able to protect them when there are 
            // multiple threads running
        volatile float price;
        volatile int quantity;
    }

    /**
     * Every stock have its own tickerbook.
     * Count: valid order number remaining
     */
    private static final class TickerBook {
        final Order[] buyOrders = new Order[MAX_ORDERS_PER_SIDE];
        final Order[] sellOrders = new Order[MAX_ORDERS_PER_SIDE];
        final java.util.concurrent.atomic.AtomicInteger buyCount =
            new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger sellCount =
            new java.util.concurrent.atomic.AtomicInteger(0);
            // By using atomic int, I am able to protect them when there are 
            // multiple threads running

        TickerBook() {
            for (int i = 0; i < MAX_ORDERS_PER_SIDE; i++) {
                buyOrders[i] = new Order();
                sellOrders[i] = new Order();
            }
        }
    }

    private static final TickerBook[] orderBooks = new TickerBook[MAX_TICKERS];
    //connect 
    static {
        for (int i = 0; i < MAX_TICKERS; i++) {
            orderBooks[i] = new TickerBook();
        }
    }

    /**
     * Loop in the tickerSymbols to find index
     */
    private static int getTickerIndex(String tickerSymbol) {
        for (int i = 0; i < MAX_TICKERS; i++) {
            String s = tickerSymbols[i];
            if (s == null) {
                break; 
            }
            if (s.equals(tickerSymbol)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Adding new tickertickerSymbol or returning index
     */
    private static int registerTickerSymbol(String tickerSymbol) {
        // Check if the ticker is already registered and return its index if so
        int idx = getTickerIndex(tickerSymbol);
        if (idx >= 0) {
            return idx; 
        }
        // If not found, loop for the first empty slot and register the ticker
        for (int i = 0; i < MAX_TICKERS; i++) {
            if (tickerSymbols[i] == null) {
                tickerSymbols[i] = tickerSymbol;
                return i;
            }
        }
        return -1;
    }

    /**
     * addOrder
     * @param orderType "Buy" or "Sell"
     * @param tickerSymbol 
     * @param quantity 
     * @param price 
     */
    public static void addOrder(String orderType, String tickerSymbol, int quantity, float price) {
        int idx = getTickerIndex(tickerSymbol);
        if (idx < 0) {
            idx = registerTickerSymbol(tickerSymbol);
            if (idx < 0) {
                return; //more than 1024
            }
        }
        TickerBook tb = orderBooks[idx];
        if ("Buy".equalsIgnoreCase(orderType)) {
            // For a Buy order, get the next available slot using an atomic increment
            int slot = tb.buyCount.getAndIncrement(); 
            if (slot < MAX_ORDERS_PER_SIDE) {
                Order od = tb.buyOrders[slot];
                od.price = price;
                od.quantity = quantity;
                od.active.set(true); 
                //tb.buyCount = slot + 1;
            }else{
                tb.buyCount.decrementAndGet(); 
            }
        } else if ("Sell".equalsIgnoreCase(orderType)) {
            int slot = tb.sellCount.getAndIncrement();
            if (slot < MAX_ORDERS_PER_SIDE) {
                Order od = tb.sellOrders[slot];
                od.price = price;
                od.quantity = quantity;
                od.active.set(true);
                //tb.sellCount = slot + 1;
            } else {
                tb.sellCount.decrementAndGet();
            }
        }
    }

    /**
     * matchOrder
     * Buy price for a particular ticker is greater than or equal to lowest Sell 
     * price available.
     * O(n)
     */
    public static void matchOrder(String tickerSymbol) {
        int idx = getTickerIndex(tickerSymbol);
        if (idx < 0) {
            return;
        }
        TickerBook tb = orderBooks[idx];
        // Find the best (highest) active Buy order
        float bestBuyPrice = -1.0f;
        int bestBuyIndex = -1;
        int bc = tb.buyCount.get();
        for (int i = 0; i < bc; i++) {
            Order od = tb.buyOrders[i];
            if (od.active.get()) {
                float p = od.price;
                if (p > bestBuyPrice) {
                    bestBuyPrice = p;
                    bestBuyIndex = i;
                }
            }
        }

        float bestSellPrice = Float.MAX_VALUE;
        int bestSellIndex = -1;
        int sc = tb.sellCount.get();
        for (int i = 0; i < sc; i++) {
            Order od = tb.sellOrders[i];
            if (od.active.get()) {
                float p = od.price;
                if (p < bestSellPrice) {
                    bestSellPrice = p;
                    bestSellIndex = i;
                }
            }
        }
        // If a valid Buy && Sell order are found and prices allow a match, we process the trade
        if (bestBuyIndex >= 0 && bestSellIndex >= 0) {
            if (bestBuyPrice >= bestSellPrice) {
                Order buyOd = tb.buyOrders[bestBuyIndex];
                Order sellOd = tb.sellOrders[bestSellIndex];
                boolean bOk = buyOd.active.compareAndSet(true, false);
                boolean sOk = sellOd.active.compareAndSet(true, false);
                if (bOk && sOk) {
                    tb.buyCount.decrementAndGet();
                    tb.sellCount.decrementAndGet();
                    System.out.println("Matched: BUY(" + buyOd.price
                        + ") vs SELL(" + sellOd.price + ") for " + tickerSymbol);
                }
            }
        }
    }
}
