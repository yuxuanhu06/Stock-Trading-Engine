public class MatchingEngine {

    private static final int MAX_TICKERS = 1024;
    private static final int MAX_ORDERS_PER_SIDE = 10000;

    /**
     * Storing 1024 ticker
     */
    private static final String[] tickerSymbols = new String[MAX_TICKERS];

    /**
     * I asked Chatgpt and do some research online that which said maybe using volatile could *getting closer with the lock free situation.
     */
    private static final class Order {
        volatile boolean active;  //True == still valid
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
        volatile int buyCount = 0;
        volatile int sellCount = 0;

        TickerBook() {
            for (int i = 0; i < MAX_ORDERS_PER_SIDE; i++) {
                buyOrders[i] = new Order();
                sellOrders[i] = new Order();
            }
        }
    }

    private static final TickerBook[] orderBooks = new TickerBook[MAX_TICKERS];

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
        int idx = getTickerIndex(tickerSymbol);
        if (idx >= 0) {
            return idx; 
        }
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
            int slot = tb.buyCount;  
            if (slot < MAX_ORDERS_PER_SIDE) {
                tb.buyOrders[slot].price = price;
                tb.buyOrders[slot].quantity = quantity;
                tb.buyOrders[slot].active = true;
                tb.buyCount = slot + 1;
            }
        } else if ("Sell".equalsIgnoreCase(orderType)) {
            int slot = tb.sellCount;
            if (slot < MAX_ORDERS_PER_SIDE) {
                tb.sellOrders[slot].price = price;
                tb.sellOrders[slot].quantity = quantity;
                tb.sellOrders[slot].active = true;
                tb.sellCount = slot + 1;
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

        float bestBuyPrice = -1.0f;
        int bestBuyIndex = -1;
        int bc = tb.buyCount;  
        for (int i = 0; i < bc; i++) {
            Order od = tb.buyOrders[i];
            if (od.active) {
                float p = od.price;
                if (p > bestBuyPrice) {
                    bestBuyPrice = p;
                    bestBuyIndex = i;
                }
            }
        }

        float bestSellPrice = Float.MAX_VALUE;
        int bestSellIndex = -1;
        int sc = tb.sellCount;
        for (int i = 0; i < sc; i++) {
            Order od = tb.sellOrders[i];
            if (od.active) {
                float p = od.price;
                if (p < bestSellPrice) {
                    bestSellPrice = p;
                    bestSellIndex = i;
                }
            }
        }

        if (bestBuyIndex >= 0 && bestSellIndex >= 0) {
            if (bestBuyPrice >= bestSellPrice) {
                Order buyOd = tb.buyOrders[bestBuyIndex];
                Order sellOd = tb.sellOrders[bestSellIndex];
                buyOd.active = false;
                sellOd.active = false;
                tb.buyCount = tb.buyCount - 1;
                tb.sellCount = tb.sellCount - 1;
                System.out.println(
                        "Matched: BUY(" + buyOd.price + ") vs SELL(" + sellOd.price
                                + ") for " + tickerSymbol);
            }
        }
    }


    public static void main(String[] args) {
        registerTickerSymbol("TKR0001");
        registerTickerSymbol("ABC");
        addOrder("Buy",  "TKR0001", 100, 95.5f);
        addOrder("Buy",  "TKR0001", 200, 90.0f);
        addOrder("Sell", "TKR0001", 50,  92.0f);
        addOrder("Sell", "TKR0001", 80,  99.0f);

        matchOrder("TKR0001");

        matchOrder("TKR0001");

        addOrder("Buy",  "ABC", 10,  102.0f);
        addOrder("Sell", "ABC", 5,   110.0f);
        matchOrder("ABC");
    }
}
