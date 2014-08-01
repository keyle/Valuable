import org.jsoup.Jsoup

def getValues() {

    def listOfStocks = [
            "CSCO", "GOOG", "AAPL", "FB", "TWTR", "MSFT", "YHOO", "AMZN", "INTC", "NCK.AX"
    ]

    listOfStocks.each { getKeyInfo(it) }
}

def getKeyInfo(stock) {
    def page = Jsoup.connect("http://finance.yahoo.com/q/ks?s=${stock}+Key+Statistics").get()
    def tds = page.select("td")

    def bv = -1;
    def roe = -1;
    def payout = -1;

    tds.eachWithIndex { td, i ->

        if (td.text() == "Book Value Per Share (mrq):") {
            bv = tds[i + 1].text().toDouble()
            assert bv != -1, "Error retrieving book value from yahoo!"
        }

        if (td.text() == "Return on Equity (ttm):") {
            roe = tds[i + 1].text().replace("%", "").toDouble()
            assert roe != -1, "Error retrieving roe from yahoo!"
        }

        if (td.text() == "Payout Ratio4:") {
            def temp = tds[i + 1].text().replace("%", "")
            if (temp != "N/A")
                payout = temp.toDouble();
            else
                payout = fetchPayoutFromReuters(stock)
        }
    }

    def result = [stockName: stock, bookValue: bv, roe: roe / 100, payoutRatio: payout / 100]

    println result
}

def fetchPayoutFromReuters(stock) {

    def page = Jsoup.connect("http://www.reuters.com/finance/stocks/financialHighlights?symbol=${stock}").get()
    def tds = page.select("td")

    def payout = -1;

    tds.eachWithIndex { td, i ->
        if (td.text() == "Payout Ratio(TTM)") {
            def temp = tds[i + 1].text()
            if (temp != "--")
                payout = temp.toDouble()
        }
    }

    return payout
}

getValues()
