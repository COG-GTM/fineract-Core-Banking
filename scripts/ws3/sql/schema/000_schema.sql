--
-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements. See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership. The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License. You may obtain a copy of the License at
--
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations
-- under the License.
--
-- WS3 cross-engine equivalence harness: schema subset.
--
-- This is a deliberately small, dialect-neutral subset of the Fineract tenant
-- schema. It carries only the tables and columns touched by the financially
-- significant read paths exercised in sql/equivalence and by the dialect
-- reproductions in sql/dialect. Column names and types mirror the tenant
-- changelog so that the SQL under test can be copied verbatim from the
-- application source.
--
-- Used only by --schema subset (the default). In --schema live mode the
-- harness runs against a real Liquibase-managed tenant database on both
-- engines and never executes this file.
--

DROP TABLE IF EXISTS m_account_transfer_transaction;
DROP TABLE IF EXISTS m_account_transfer_details;
DROP TABLE IF EXISTS m_savings_account_charge;
DROP TABLE IF EXISTS m_savings_account_transaction;
DROP TABLE IF EXISTS m_savings_account;
DROP TABLE IF EXISTS acc_gl_journal_entry;
DROP TABLE IF EXISTS acc_gl_account;
DROP TABLE IF EXISTS m_loan_transaction;
DROP TABLE IF EXISTS m_loan_repayment_schedule;
DROP TABLE IF EXISTS m_loan;
DROP TABLE IF EXISTS m_charge;
DROP TABLE IF EXISTS m_address;
DROP TABLE IF EXISTS m_client;
DROP TABLE IF EXISTS m_office;
DROP TABLE IF EXISTS m_currency;

CREATE TABLE m_currency (
    code VARCHAR(3) NOT NULL,
    decimal_places SMALLINT NOT NULL,
    currency_multiplesof SMALLINT,
    display_symbol VARCHAR(10),
    name VARCHAR(50) NOT NULL,
    internationalized_name_code VARCHAR(50) NOT NULL,
    PRIMARY KEY (code)
);

CREATE TABLE m_office (
    id BIGINT NOT NULL,
    parent_id BIGINT,
    hierarchy VARCHAR(100),
    name VARCHAR(100) NOT NULL,
    opening_date DATE NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE m_client (
    id BIGINT NOT NULL,
    office_id BIGINT NOT NULL,
    account_no VARCHAR(20) NOT NULL,
    status_enum INTEGER NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    firstname VARCHAR(50),
    middlename VARCHAR(50),
    lastname VARCHAR(50),
    date_of_birth DATE,
    PRIMARY KEY (id)
);

CREATE TABLE m_address (
    id BIGINT NOT NULL,
    street VARCHAR(100),
    address_line_1 VARCHAR(100),
    address_line_2 VARCHAR(100),
    city VARCHAR(100),
    postal_code VARCHAR(30),
    PRIMARY KEY (id)
);

CREATE TABLE m_charge (
    id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    charge_time_enum SMALLINT NOT NULL,
    is_active BOOLEAN NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE m_loan (
    id BIGINT NOT NULL,
    account_no VARCHAR(20) NOT NULL,
    client_id BIGINT,
    currency_code VARCHAR(3) NOT NULL,
    currency_digits SMALLINT NOT NULL,
    currency_multiplesof SMALLINT,
    loan_status_id SMALLINT NOT NULL,
    principal_amount NUMERIC(19, 6),
    principal_disbursed_derived NUMERIC(19, 6),
    principal_repaid_derived NUMERIC(19, 6),
    principal_writtenoff_derived NUMERIC(19, 6),
    principal_outstanding_derived NUMERIC(19, 6),
    interest_charged_derived NUMERIC(19, 6),
    interest_repaid_derived NUMERIC(19, 6),
    interest_waived_derived NUMERIC(19, 6),
    interest_writtenoff_derived NUMERIC(19, 6),
    interest_outstanding_derived NUMERIC(19, 6),
    fee_charges_charged_derived NUMERIC(19, 6),
    fee_charges_repaid_derived NUMERIC(19, 6),
    fee_charges_outstanding_derived NUMERIC(19, 6),
    penalty_charges_charged_derived NUMERIC(19, 6),
    penalty_charges_repaid_derived NUMERIC(19, 6),
    penalty_charges_outstanding_derived NUMERIC(19, 6),
    total_expected_repayment_derived NUMERIC(19, 6),
    total_repayment_derived NUMERIC(19, 6),
    total_outstanding_derived NUMERIC(19, 6),
    total_overpaid_derived NUMERIC(19, 6),
    net_disbursal_amount NUMERIC(19, 6),
    is_npa BOOLEAN NOT NULL,
    disbursedon_date DATE,
    closedon_date DATE,
    PRIMARY KEY (id)
);

CREATE TABLE m_loan_repayment_schedule (
    id BIGINT NOT NULL,
    loan_id BIGINT NOT NULL,
    installment SMALLINT NOT NULL,
    fromdate DATE,
    duedate DATE NOT NULL,
    obligations_met_on_date DATE,
    completed_derived BOOLEAN NOT NULL,
    principal_amount NUMERIC(19, 6),
    principal_completed_derived NUMERIC(19, 6),
    principal_writtenoff_derived NUMERIC(19, 6),
    interest_amount NUMERIC(19, 6),
    interest_completed_derived NUMERIC(19, 6),
    interest_writtenoff_derived NUMERIC(19, 6),
    interest_waived_derived NUMERIC(19, 6),
    fee_charges_amount NUMERIC(19, 6),
    fee_charges_completed_derived NUMERIC(19, 6),
    fee_charges_writtenoff_derived NUMERIC(19, 6),
    fee_charges_waived_derived NUMERIC(19, 6),
    penalty_charges_amount NUMERIC(19, 6),
    penalty_charges_completed_derived NUMERIC(19, 6),
    penalty_charges_writtenoff_derived NUMERIC(19, 6),
    penalty_charges_waived_derived NUMERIC(19, 6),
    PRIMARY KEY (id)
);

CREATE TABLE m_loan_transaction (
    id BIGINT NOT NULL,
    loan_id BIGINT NOT NULL,
    office_id BIGINT NOT NULL,
    transaction_type_enum SMALLINT NOT NULL,
    transaction_date DATE NOT NULL,
    is_reversed BOOLEAN NOT NULL,
    amount NUMERIC(19, 6) NOT NULL,
    principal_portion_derived NUMERIC(19, 6),
    interest_portion_derived NUMERIC(19, 6),
    fee_charges_portion_derived NUMERIC(19, 6),
    penalty_charges_portion_derived NUMERIC(19, 6),
    outstanding_loan_balance_derived NUMERIC(19, 6),
    PRIMARY KEY (id)
);

CREATE TABLE acc_gl_account (
    id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    gl_code VARCHAR(45) NOT NULL,
    classification_enum SMALLINT NOT NULL,
    account_usage SMALLINT NOT NULL,
    manual_entries_allowed BOOLEAN NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE acc_gl_journal_entry (
    id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    office_id BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    transaction_id VARCHAR(50) NOT NULL,
    reversed BOOLEAN NOT NULL,
    manual_entry BOOLEAN NOT NULL,
    entry_date DATE NOT NULL,
    type_enum SMALLINT NOT NULL,
    amount NUMERIC(19, 6) NOT NULL,
    is_running_balance_calculated BOOLEAN NOT NULL,
    office_running_balance NUMERIC(19, 6),
    organization_running_balance NUMERIC(19, 6),
    PRIMARY KEY (id)
);

CREATE TABLE m_savings_account (
    id BIGINT NOT NULL,
    account_no VARCHAR(20) NOT NULL,
    client_id BIGINT,
    currency_code VARCHAR(3) NOT NULL,
    currency_digits SMALLINT NOT NULL,
    status_enum SMALLINT NOT NULL,
    account_balance_derived NUMERIC(19, 6) NOT NULL,
    total_deposits_derived NUMERIC(19, 6),
    total_withdrawals_derived NUMERIC(19, 6),
    total_interest_posted_derived NUMERIC(19, 6),
    total_fees_charge_derived NUMERIC(19, 6),
    min_required_balance NUMERIC(19, 6),
    activatedon_date DATE,
    PRIMARY KEY (id)
);

CREATE TABLE m_savings_account_transaction (
    id BIGINT NOT NULL,
    savings_account_id BIGINT NOT NULL,
    office_id BIGINT NOT NULL,
    transaction_type_enum SMALLINT NOT NULL,
    transaction_date DATE NOT NULL,
    is_reversed BOOLEAN NOT NULL,
    is_manual BOOLEAN NOT NULL,
    amount NUMERIC(19, 6) NOT NULL,
    running_balance_derived NUMERIC(19, 6),
    balance_end_date_derived DATE,
    PRIMARY KEY (id)
);

CREATE TABLE m_savings_account_charge (
    id BIGINT NOT NULL,
    savings_account_id BIGINT NOT NULL,
    charge_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL,
    amount NUMERIC(19, 6) NOT NULL,
    amount_paid_derived NUMERIC(19, 6),
    amount_outstanding_derived NUMERIC(19, 6),
    PRIMARY KEY (id)
);

CREATE TABLE m_account_transfer_details (
    id BIGINT NOT NULL,
    from_office_id BIGINT NOT NULL,
    to_office_id BIGINT NOT NULL,
    from_loan_account_id BIGINT,
    to_loan_account_id BIGINT,
    from_savings_account_id BIGINT,
    to_savings_account_id BIGINT,
    transfer_type SMALLINT NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE m_account_transfer_transaction (
    id BIGINT NOT NULL,
    account_transfer_details_id BIGINT NOT NULL,
    from_loan_transaction_id BIGINT,
    to_loan_transaction_id BIGINT,
    is_reversed BOOLEAN NOT NULL,
    transaction_date DATE NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    amount NUMERIC(19, 6) NOT NULL,
    PRIMARY KEY (id)
);
