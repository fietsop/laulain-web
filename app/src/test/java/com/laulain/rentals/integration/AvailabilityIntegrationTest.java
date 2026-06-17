package com.laulain.rentals.integration;

import com.laulain.rentals.dto.AvailabilityDto.*;
import com.laulain.rentals.model.*;
import com.laulain.rentals.repository.*;
import com.laulain.rentals.service.AvailabilityService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Availability Engine Integration Tests")
class AvailabilityIntegrationTest extends BaseIntegrationTest {

    @Autowired AvailabilityService availabilityService;
    @Autowired ItemRepository itemRepository;
    @Autowired ItemCategoryRepository categoryRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired AvailabilityBlockRepository blockRepository;

    private Item item;
    private Customer customer;
    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(30);

    @BeforeEach
    @Transactional
    void setUp() {
        ItemCategory cat = categoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc()
                .stream().findFirst()
                .orElseGet(() -> categoryRepository.save(ItemCategory.builder()
                        .name("Avail Test Cat " + UUID.randomUUID()).active(true).build()));

        item = itemRepository.save(Item.builder()
                .name("Availability Test Item " + UUID.randomUUID().toString().substring(0, 6))
                .slug("avail-test-" + UUID.randomUUID().toString().substring(0, 8))
                .category(cat)
                .pricePerDay(new BigDecimal("25.00"))
                .quantityInStock(2)
                .active(true).featured(false).images(new ArrayList<>())
                .build());

        customer = customerRepository.save(Customer.builder()
                .firstName("Test").lastName("Customer")
                .email("avail-test-" + UUID.randomUUID() + "@test.com")
                .build());
    }

    @Test
    @DisplayName("Item is available when no blocks exist")
    void availableWhenNoBlocks() {
        AvailabilityCheckRequest req = new AvailabilityCheckRequest(
                item.getId(), FUTURE_DATE, FUTURE_DATE, 1);
        AvailabilityCheckResponse response = availabilityService.checkAvailability(req);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getAvailableQuantity()).isEqualTo(2);
        assertThat(response.getUnavailableDates()).isEmpty();
    }

    @Test
    @DisplayName("Partial block still allows booking with reduced quantity")
    @Transactional
    void partialBlockAllowsReducedQty() {
        // Block 1 of 2 units
        blockRepository.save(AvailabilityBlock.builder()
                .item(item).blockDate(FUTURE_DATE).quantity(1).reason("BOOKING").build());

        AvailabilityCheckRequest req = new AvailabilityCheckRequest(
                item.getId(), FUTURE_DATE, FUTURE_DATE, 1);
        AvailabilityCheckResponse response = availabilityService.checkAvailability(req);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getAvailableQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Fully blocked item returns unavailable")
    @Transactional
    void fullyBlockedIsUnavailable() {
        // Block all 2 units
        blockRepository.save(AvailabilityBlock.builder()
                .item(item).blockDate(FUTURE_DATE).quantity(2).reason("BOOKING").build());

        AvailabilityCheckRequest req = new AvailabilityCheckRequest(
                item.getId(), FUTURE_DATE, FUTURE_DATE, 1);
        AvailabilityCheckResponse response = availabilityService.checkAvailability(req);

        assertThat(response.isAvailable()).isFalse();
        assertThat(response.getAvailableQuantity()).isEqualTo(0);
        assertThat(response.getUnavailableDates()).contains(FUTURE_DATE);
    }

    @Test
    @DisplayName("createBlocksForBooking creates blocks including buffer days")
    @Transactional
    void createBlocksIncludesBufferDays() {
        Booking booking = bookingRepository.save(Booking.builder()
                .bookingNumber("LLR-TEST-" + UUID.randomUUID().toString().substring(0, 6))
                .customer(customer)
                .status(Booking.Status.CONFIRMED)
                .eventDate(FUTURE_DATE)
                .eventType("Test Event")
                .deliveryFee(BigDecimal.ZERO).setupFee(BigDecimal.ZERO)
                .subtotal(BigDecimal.ZERO).taxAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO).depositAmount(BigDecimal.ZERO)
                .balanceDue(BigDecimal.ZERO)
                .bookingItems(new ArrayList<>())
                .build());

        BookingItem lineItem = BookingItem.builder()
                .booking(booking).item(item).itemName(item.getName())
                .quantity(1).unitPrice(item.getPricePerDay())
                .lineTotal(item.getPricePerDay()).rentalDays(1)
                .build();
        booking.getBookingItems().add(lineItem);
        bookingRepository.save(booking);

        availabilityService.createBlocksForBooking(booking);

        // bufferDays = 1 → blocks on: FUTURE_DATE-1, FUTURE_DATE, FUTURE_DATE+1 = 3 blocks
        var blocks = blockRepository.findBlocksForItem(
                item.getId(),
                FUTURE_DATE.minusDays(2),
                FUTURE_DATE.plusDays(2)
        );
        assertThat(blocks).hasSize(3);
        assertThat(blocks).extracting(AvailabilityBlock::getBlockDate)
                .contains(FUTURE_DATE.minusDays(1), FUTURE_DATE, FUTURE_DATE.plusDays(1));
    }

    @Test
    @DisplayName("releaseBlocksForBooking removes all blocks for that booking")
    @Transactional
    void releaseBlocksRemovesAll() {
        Booking booking = bookingRepository.save(Booking.builder()
                .bookingNumber("LLR-REL-" + UUID.randomUUID().toString().substring(0, 6))
                .customer(customer)
                .status(Booking.Status.CONFIRMED)
                .eventDate(FUTURE_DATE)
                .eventType("Test Event")
                .deliveryFee(BigDecimal.ZERO).setupFee(BigDecimal.ZERO)
                .subtotal(BigDecimal.ZERO).taxAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO).depositAmount(BigDecimal.ZERO)
                .balanceDue(BigDecimal.ZERO)
                .bookingItems(new ArrayList<>())
                .build());

        // Manually create 3 blocks
        for (int i = -1; i <= 1; i++) {
            blockRepository.save(AvailabilityBlock.builder()
                    .item(item).booking(booking)
                    .blockDate(FUTURE_DATE.plusDays(i))
                    .quantity(1).reason("BOOKING").build());
        }

        assertThat(blockRepository.findByBookingId(booking.getId())).hasSize(3);

        availabilityService.releaseBlocksForBooking(booking.getId());

        assertThat(blockRepository.findByBookingId(booking.getId())).isEmpty();
    }

    @Test
    @DisplayName("Monthly availability returns correct day statuses")
    @Transactional
    void monthlyAvailabilityStatuses() {
        // Block future date fully
        blockRepository.save(AvailabilityBlock.builder()
                .item(item).blockDate(FUTURE_DATE).quantity(2).reason("BOOKING").build());

        int year  = FUTURE_DATE.getYear();
        int month = FUTURE_DATE.getMonthValue();

        MonthlyAvailability monthly = availabilityService.getMonthlyAvailability(
                item.getId(), year, month);

        assertThat(monthly.getYear()).isEqualTo(year);
        assertThat(monthly.getMonth()).isEqualTo(month);
        assertThat(monthly.getDays()).isNotEmpty();

        // The blocked date should be FULLY_BOOKED
        DayAvailability blockedDay = monthly.getDays().stream()
                .filter(d -> d.getDate().equals(FUTURE_DATE))
                .findFirst().orElseThrow();
        assertThat(blockedDay.getStatus()).isEqualTo(DayAvailability.DayStatus.FULLY_BOOKED);
        assertThat(blockedDay.getAvailableQuantity()).isEqualTo(0);
    }
}
