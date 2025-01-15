
package com.example.demo;


import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.*;

import com.example.demo.service.Impl.PortfolioServiceImpl;
import com.example.demo.service.Impl.StockServiceImpl;
import com.example.demo.service.Impl.TradeServiceImpl;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.multipart.MultipartFile;


import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class DemoApplicationTests {

//	@Autowired
//	private PortfolioService portfolioService;
//
//	@Autowired
//	private StockService stockService;

//	@Autowired
//	private StockRepository stockRepository;

	 TradeRepository tradeRepository = mock(TradeRepository.class);
	 StockRepository stockRepository  = mock(StockRepository.class);
	 UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);

	 PortfolioService portfolioService= new PortfolioServiceImpl(tradeRepository,stockRepository);
	 StockService stockService = new StockServiceImpl(stockRepository);
	 TradeService tradeService = new TradeServiceImpl(tradeRepository,stockRepository,userAccountRepository);


//	@Autowired
//	private UserAccountRepository userAccountRepository;
//
//
//	private TestUtility testUtility = new TestUtility();

	@BeforeEach
	void setup(){
		System.out.println("Setup starting");
		userAccountRepository.deleteAll();
		stockRepository.deleteAll();
		tradeRepository.deleteAll();

			userAccountRepository.save(new UserAccount(0L,"User"+0,"user" +0+ "@example.com", LocalDateTime.now()));
			userAccountRepository.save(new UserAccount(1L,"User"+1,"user" +1+ "@example.com", LocalDateTime.now()));

		// Add 10 Stocks
        IntStream.rangeClosed(1, 10).forEach(i -> {
            Stock stock = new Stock();
            stock.setName("Stock" + i);
            stock.setOpenPrice(100.0 + i);
            stock.setClosePrice(105.0 + i);
            stock.setHighPrice(110.0 + i);
            stock.setLowPrice(95.0 + i);
            stock.setSettlementPrice(102.5 + i);
            stockRepository.save(stock);
        });

        // Add 10 Trades for each user
        userAccountRepository.findAll().forEach(user -> {
					stockRepository.findAll().forEach(stock -> {
						Trade trade = new Trade();
						trade.setUserAccount(user);
						trade.setStock(stock);
						trade.setTradeType("BUY");
						trade.setQuantity(10);
						trade.setPrice(stock.getOpenPrice());
						tradeRepository.save(trade);
					});
				});

		System.out.println("Setup complete");
	}

	@SneakyThrows
	@Test
	void testGetPortfolioForUser() {
		PortfolioResponseDTO response = portfolioService.getPortfolio(1L);
//		assertTrue(true);
		assertNotNull(response);
		assertEquals(10, response.getHoldings().size());
		assertTrue(response.getTotalHoldingValue() > 0);
		assertTrue(response.getTotalPLPercentage() >= -100);
	}

//	@Test
//	void testGetAllStocks() {
//		List<Stock> stocks = testUtility.getAllStocks();
//
//		assertNotNull(stocks);
//		assertEquals(10, stocks.size());
//		assertEquals("Stock1", stocks.get(0).getName());
//	}

//	@Test
//	void testGetUserById() {
//		UserAccount user = userAccountRepository.findById(1L).orElse(null);
//
//		assertNotNull(user);
//		assertEquals("User1", user.getName());
//		assertEquals("user1@example.com", user.getEmail());
//	}

	@Test
	void testFetchAllUser(){

		List<UserAccount> users=new ArrayList<>();
		when(userAccountRepository.findAll()).thenReturn(users);
		verify(userAccountRepository).findAll();
		assertNotNull(users);
//		assertEquals(10,users.size());

	}
//
//	@Test
//	void testUpdateStocksFromCsv() {
//		String csvData = "Stock11,110,115,120,105,112.5\nStock12,120,125,130,115,122.5";
//		testUtility.updateStocksFromCsv(csvData);
//
//		List<Stock> stocks = stockRepository.findAll();
//		assertEquals(12, stocks.size());
//		assertEquals("Stock11", stocks.get(10).getName());
//	}

//	@Test
//	void testTradeRepositoryData() {
//		//todo register trade for a user
//
//		// Get trades for a user
//		List<Trade> trades = tradeRepository.findByUserAccountId(1L);
//		System.out.println(trades.size());
//		assertNotNull(trades);
//		assertEquals(10, trades.size());
//		trades.forEach(trade -> assertEquals("BUY", trade.getTradeType()));
//	}

//	@Test
//	void testUserAccountRepositoryData() {
//		List<UserAccount> users = userAccountRepository.findAll();
//
//		assertNotNull(users);
//		assertEquals(10, users.size());
//		assertEquals("User1", users.get(0).getName());
//	}
//
//	@Test
//	void testStockRepositoryData() {
//		List<Stock> stocks = stockRepository.findAll();
//
//		assertNotNull(stocks);
//		assertEquals(10, stocks.size());
//		assertTrue(stocks.get(0).getOpenPrice() > 0);
//	}


}
