@Grab(group = 'org.codehaus.gpars', module = 'gpars', version = '1.0.0')
@Grapes(@Grab('org.jsoup:jsoup:1.7.3'))

import groovyx.gpars.GParsPool
import org.jsoup.Jsoup

def init() {

    def stocks = [
//            "CSCO", "GOOG", "AAPL", "FB", "TWTR", "MSFT", "YHOO", "AMZN", "INTC", "HPQ", "JRN.N"
"JRN.N", "GPX.N", "AMRC.N", "EIG.N", "TAOM.N"
    ]

    def financials = []

    GParsPool.withPool {
        stocks.eachParallel { stock -> financials.add(getFinancials(stock)) }
    }

    financials.each { valueCompany(it) }

    sleep(2000)
}

def getFinancials(stock) {
    def page = Jsoup.connect("http://www.reuters.com/finance/stocks/financialHighlights?symbol=${stock}")
            .userAgent("Mozilla")
            .timeout(6000)
            .get()

    def tds = page.select("td")

    def priceToBook = -1
    def bv = -1
    def roe = -1
    def payout = -1
    def tax = -1

    def price = page.select(".sectionQuoteDetail span")[1].text().trim().toFloat()
    assert price > 0, "Error retrieving price : ${price}"

    tds.eachWithIndex { td, i ->

        if (td.text() == "Price to Book (MRQ)") {
            def tmpbv = tds[i + 1].text()
            if (tmpbv != "--")
                priceToBook = tmpbv.toFloat()
        }

        if (td.text() == "Return on Equity (TTM)") {
            def temp = tds[i + 1].text()
            if (temp != "--") {
                roe = temp.toFloat() / 100
                roe = roe.round(4)
            }
        }

        if (td.text() == "Payout Ratio(TTM)") {
            def temp = tds[i + 1].text()
            if (temp != "--") {
                payout = temp.toFloat() / 100
                payout = payout.round(4)
            }
        }

        if (td.text() == "Effective Tax Rate (TTM)") {
            def temp = tds[i + 1].text()
            if (temp != "--") {
                tax = temp.toFloat() / 100
                tax = tax.round(4)
            }
        }
    }

    if (priceToBook != -1) {
        bv = price / priceToBook
        bv = bv.round(4)
    }

    if (tax < 0)
        tax = 0

    if (payout < 0)
        payout = 0

    if (bv < 0)
        bv = 0

    if (roe < 0)
        roe = 0

    def result = [stockName: stock, bv: bv, roe: roe, payout: payout, tax: tax, price: price]

    println result

    return result
}


def valueCompany(c) {

    def IRR = (c.roe * (1 - c.payout)) + (c.roe * c.payout * (1 - c.tax))

    def RR = 0.08

    def val = c.bv * (IRR / RR) * Math.pow(1 + IRR - RR, 5)
    val = val.round(2)

    def diff = val / c.price
    diff = diff.round(2)

    println "${c.stockName} valued \t${val} \t (${c.price}) \t ${diff}%"
}

init()

