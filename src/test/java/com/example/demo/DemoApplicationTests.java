
package com.example.demo;


import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.*;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.multipart.MultipartFile;


import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@SpringJUnitConfig(classes = {PortfolioService.class, StockService.class})
@Import({StockRepository.class, TradeRepository.class, UserAccountRepository.class})
class DemoApplicationTests {

	@Autowired
	private PortfolioService portfolioService;

	@Autowired
	private StockService stockService;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private TradeRepository tradeRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private DummyDataInitializer dummyDataInitializer;

	@Autowired
	private TestUtility testUtility;

	@BeforeEach
	void setup(){
		dummyDataInitializer.init();
	}

	@SneakyThrows
	@Test
	void testGetPortfolioForUser() {
		PortfolioResponseDTO response = testUtility.getPortfolioForUser(1L);

		assertNotNull(response);
		assertEquals(10, response.getHoldings().size());
		assertTrue(response.getTotalHoldingValue() > 0);
		assertTrue(response.getTotalPLPercentage() >= -100);
	}

	@Test
	void testGetAllStocks() {
		List<Stock> stocks = testUtility.getAllStocks();

		assertNotNull(stocks);
		assertEquals(10, stocks.size());
		assertEquals("Stock1", stocks.get(0).getName());
	}

	@Test
	void testGetUserById() {
		UserAccount user = testUtility.getUserById(1L);

		assertNotNull(user);
		assertEquals("User1", user.getName());
		assertEquals("user1@example.com", user.getEmail());
	}

	@Test
	void testUpdateStocksFromCsv() {
		String csvData = "Stock11,110,115,120,105,112.5\nStock12,120,125,130,115,122.5";
		testUtility.updateStocksFromCsv(csvData);

		List<Stock> stocks = stockRepository.findAll();
		assertEquals(12, stocks.size());
		assertEquals("Stock11", stocks.get(10).getName());
	}

	@Test
	void testTradeRepositoryData() {
		List<Trade> trades = tradeRepository.findByUserAccountId(1L);

		assertNotNull(trades);
		assertEquals(10, trades.size());
		trades.forEach(trade -> assertEquals("BUY", trade.getTradeType()));
	}

	@Test
	void testUserAccountRepositoryData() {
		List<UserAccount> users = userAccountRepository.findAll();

		assertNotNull(users);
		assertEquals(10, users.size());
		assertEquals("User1", users.get(0).getName());
	}

	@Test
	void testStockRepositoryData() {
		List<Stock> stocks = stockRepository.findAll();

		assertNotNull(stocks);
		assertEquals(10, stocks.size());
		assertTrue(stocks.get(0).getOpenPrice() > 0);
	}

	@Test
	void testPortfolioHoldingsCalculation() {
		PortfolioResponseDTO response = testUtility.getPortfolioForUser(1L);

		assertNotNull(response);
		response.getHoldings().forEach(holding -> {
			assertTrue(holding.getCurrentPrice() >= holding.getBuyPrice());
			assertTrue(holding.getGainLoss() >= 0);
		});
	}

	@Test
	void testCsvProcessingInvalidData() {
		String invalidCsv = "Invalid,Data\nWrong,Format";
		MockMultipartFile file = new MockMultipartFile("invalid.csv", invalidCsv.getBytes(StandardCharsets.UTF_8));

		RuntimeException exception = assertThrows(RuntimeException.class, () -> {
			testUtility.updateStocksFromCsv(invalidCsv);
		});

		assertTrue(exception.getMessage().contains("Failed to process CSV"));
	}

	@Test
	void testStockDataAfterCsvUpdate() {
		String csvData = "Stock13,130,135,140,125,132.5";
		testUtility.updateStocksFromCsv(csvData);

		Stock stock = stockRepository.findByName("Stock13").get(0);
		assertNotNull(stock);
		assertEquals(132.5, stock.getSettlementPrice());
	}
}
