package com.laulain.rentals.repository;

import com.laulain.rentals.model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID> {

    Optional<Contract> findByDocusignEnvelopeId(String envelopeId);

    Optional<Contract> findByContractNumber(String contractNumber);

    List<Contract> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    Optional<Contract> findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
            UUID bookingId, Contract.Status status);

    @Query(value = "SELECT nextval('contract_number_seq')", nativeQuery = true)
    long nextContractSequence();
}
