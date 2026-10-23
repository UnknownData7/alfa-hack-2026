package ru.hackathon.credit.company;

import java.time.LocalDate;
import java.time.Period;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "company")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String inn;
    private String name;

    @Column(name = "legal_form")
    private String legalForm;

    private String ogrn;

    @Column(name = "okved_code")
    private String okvedCode;

    @Column(name = "okved_name")
    private String okvedName;

    @Column(name = "industry_risk")
    private String industryRisk;

    @Column(name = "registration_date")
    private LocalDate registrationDate;

    @Column(name = "employees_count")
    private int employeesCount;

    @Column(name = "tax_system")
    private String taxSystem;

    @Column(name = "account_masked")
    private String accountMasked;

    @Column(name = "months_with_bank")
    private int monthsWithBank;

    public int businessAgeMonths(LocalDate today) {
        return (int) Period.between(registrationDate, today).toTotalMonths();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInn() {
        return inn;
    }

    public void setInn(String inn) {
        this.inn = inn;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLegalForm() {
        return legalForm;
    }

    public void setLegalForm(String legalForm) {
        this.legalForm = legalForm;
    }

    public String getOgrn() {
        return ogrn;
    }

    public void setOgrn(String ogrn) {
        this.ogrn = ogrn;
    }

    public String getOkvedCode() {
        return okvedCode;
    }

    public void setOkvedCode(String okvedCode) {
        this.okvedCode = okvedCode;
    }

    public String getOkvedName() {
        return okvedName;
    }

    public void setOkvedName(String okvedName) {
        this.okvedName = okvedName;
    }

    public String getIndustryRisk() {
        return industryRisk;
    }

    public void setIndustryRisk(String industryRisk) {
        this.industryRisk = industryRisk;
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(LocalDate registrationDate) {
        this.registrationDate = registrationDate;
    }

    public int getEmployeesCount() {
        return employeesCount;
    }

    public void setEmployeesCount(int employeesCount) {
        this.employeesCount = employeesCount;
    }

    public String getTaxSystem() {
        return taxSystem;
    }

    public void setTaxSystem(String taxSystem) {
        this.taxSystem = taxSystem;
    }

    public String getAccountMasked() {
        return accountMasked;
    }

    public void setAccountMasked(String accountMasked) {
        this.accountMasked = accountMasked;
    }

    public int getMonthsWithBank() {
        return monthsWithBank;
    }

    public void setMonthsWithBank(int monthsWithBank) {
        this.monthsWithBank = monthsWithBank;
    }
}
