@Grab(group = 'org.codehaus.gpars', module = 'gpars', version = '1.0.0')
@Grapes(@Grab('org.jsoup:jsoup:1.7.3'))

import groovyx.gpars.GParsPool
import org.jsoup.Jsoup

import java.awt.Desktop

init()


def init() {

    def list = []

    if (!args) {
        args = []
        args[0] = 'default'
    }

    switch (args[0]) {

        case "-s":
            if (args.length < 2) {
                println "invalid syntax"
                System.exit(0)
            }
            list = args - '-s'
            println "fetching... ${list}"
            break;

        case "-o":
            if (args.length < 2) {
                println "invalid syntax"
                System.exit(0)
            }
            println "opening ${args[1]}"
            Desktop.getDesktop().browse(new URI("http://www.reuters.com/finance/stocks/financialHighlights?symbol=${args[1]}"))
            list << args[1]
            break;

        case "-h":
            printHelp()
            System.exit(0)
            break;

        default:
            println "Value : \t -s stock1 stock2"
            println "ie.     \t -s goog aapl ba"
            println "Open :  \t -o stock"
            println "Help :  \t -h"
    }

    def stocks = list ? list : ["msft", "aapl"]

    def financials = []

    try {
        GParsPool.withPool {
            stocks.eachParallel { stock -> financials.add(Valuable.getFinancials(stock)) }
        }
    } catch (ignored) {
        println "Whoops, one of these didn't exist"
    }

    println(" ------------------------------------------------------------------------------------------------------------------------------------------")


    financials.each { Valuable.valueCompany(it) }

    sleep(400)
}


class Valuable {

    static getFinancials(stock) {
        def page = Jsoup.connect("http://www.reuters.com/finance/stocks/financialHighlights?symbol=${stock}")
                .userAgent("Mozilla")
                .timeout(35000)
                .get()

        def tds = page.select("td")

        def priceToBook = -1
        def bv = -1
        def roe = -1
        def payout = -1
        def tax = -1
        def debt2equity = 0.0
        def debt2equityIndustry = 0.0

        def price = page.select(".sectionQuoteDetail span")[1].text().trim().toFloat()
        assert price > 0, "Error retrieving price : ${price}"

        def stockName = page.select("h1")[0].text().split(':')[1].trim()

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

            if (td.text() == "Total Debt to Equity (MRQ)") {
                def temp = tds[i + 1].text()
                if (temp != "--") {
                    debt2equity = temp.toFloat()
                    debt2equity = debt2equity.round(4)
                }
                def temp2 = tds[i + 2].text()
                if (temp2 != "--") {
                    debt2equityIndustry = temp2.toFloat()
                    debt2equityIndustry = debt2equityIndustry.round(4)
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

        if (roe > 0.99)
            roe = 0.99


        def result = [stock: stock, bv: bv, roe: roe, payout: payout, tax: tax, price: price,
                      name : stockName, debtRatio: debt2equity, debtIndustry: debt2equityIndustry]

        result = getOpinions(result, stock)

        println result

        return result
    }

    static getOpinions(financialResults, stock) {

        def page = Jsoup.connect("http://www.reuters.com/finance/stocks/analyst?symbol=${stock}")
                .userAgent("Mozilla")
                .timeout(35000)
                .get()

        def tds = page.select("td")

        def current = 0.0, past = 0.0

        tds.eachWithIndex { td, i ->
            if (td.text() == "Mean Rating") {
                def temp = tds[i + 1].text()
                if (temp != "--") {
                    current = temp.toFloat().round(4)
                }
                def temp2 = tds[i + 1].text()
                if (temp2 != "--") {
                    past = temp.toFloat().round(4)
                }
            }
        }

        financialResults.opinionNow = current
        financialResults.opinion1m = past

        return financialResults
    }


    static valueCompany(c) {

        def IRR = (c.roe * (1 - c.payout)) + (c.roe * c.payout * (1 - c.tax))
        def RR = 0.08
        def val = c.bv * (IRR / RR) * Math.pow(1 + IRR - RR, 5)
        val = val.round(2)

        def diff = val / c.price
        diff = diff.round(2)

        printf "| %-35.35s | valued at %7.2f | currently %7.2f | %5.2fx | %6.2f / %6.2f debt%%/indu | %4.2f <- %4.2f Opinion |\n", c.name, val, c.price, diff, c.debtRatio, c.debtIndustry, c.opinionNow, c.opinion1m
    }
}


def printHelp() {
    println "\n  -s stock: for valuation" +
            "\n  -o stock: to open in browser" +
            "\n  -h for this help" +
            "\n  " +
            "\n  This values stock using a common value formula based on ROE, BV, TAX, Payout and IRR" +
            "\n  " +
            "\n  Imagine this:\n" +
            "\n  | Oracle Corp (ORCL.N) | valued at 61.94 | currently 40.43 | 1.53x | 51.57 / 12.45 debt%/indu | 2.29 <- 2.29 Opinion |\n" +
            "\n  - value is the estimated dollar value" +
            "\n  - currently is the current price" +
            "\n  - 1.53x is a ratio of the value/price (> 1 is good)" +
            "\n  - debt% / indu is the ratio of debt leverage compared to the industry (liabilities vs. equity compared to industry) " +
            "\n  \tLow is good. High is bad. The lower the better" +
            "\n  - Opinion [x <- y] is the Reuters analysts opinion ratio." +
            "\n  \t[1 - 1.5] is very good. Higher is less good. Over 2.5 is avoid!" +
            "\n  \t[x <- y] denotes change with 'this month <- last month'"
}
