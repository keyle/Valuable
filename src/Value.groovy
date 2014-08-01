import org.jsoup.Jsoup

def getValues() {

    def listOfStocks = [
            "CSCO", "GOOG", "AAPL", "FB", "TWTR", "MSFT", "YHOO", "AMZN", "INTC"
    ]

    listOfStocks.each { getKeyInfo(it) }
}

def getKeyInfo(stock) {
    def page = Jsoup.connect("http://finance.yahoo.com/q/ks?s=${stock}+Key+Statistics").get()
    def tds = page.select("td")

    def bv = -1;
    def roe = -1;
    def payout = 0;

    tds.eachWithIndex { td, i ->

        if (td.text() == "Book Value Per Share (mrq):") {
            bv = tds[i + 1].text().toFloat()
        }

        if (td.text() == "Return on Equity (ttm):") {
            roe = tds[i + 1].text().replace("%", "").toFloat()
        }

        if (td.text() == "Payout Ratio4:") {
            def temp = tds[i + 1].text().replace("%", "").toFloat()
            if (temp != "N/A")
                payout = temp;
        }
    }

    println roe;

    assert bv != -1, "Error retrieving book value from yahoo!"
    assert roe != -1, "Error retrieving roe from yahoo!"

    // https://au.finance.yahoo.com/q/ks?s=NCK.AX
    // http://www.reuters.com/finance/stocks/financialHighlights?symbol=NCK.AX


    def result = [stockName: stock, bookValue: bv, roe: roe / 100, payoutRatio: payout / 100]

}

getValues()
