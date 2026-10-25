package ru.hackathon.credit.integration;

/** Реестр юрлиц и ИП (аналог ЕГРЮЛ/ЕГРИП): существование и статус компании. */
public interface CompanyRegistryClient {

    RegistryInfo lookup(String inn);

    record RegistryInfo(String inn, String status, String statusText, boolean active) {
    }
}
