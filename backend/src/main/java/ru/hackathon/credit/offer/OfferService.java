package ru.hackathon.credit.offer;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.hackathon.credit.application.ActorType;
import ru.hackathon.credit.application.ApplicationStatus;
import ru.hackathon.credit.application.LoanApplication;
import ru.hackathon.credit.application.StatusTransitionService;
import ru.hackathon.credit.common.ApiException;
import ru.hackathon.credit.common.Json;
import ru.hackathon.credit.pricing.PricingCalculator;
import ru.hackathon.credit.scoring.Grade;

/**
 * Предложения условий. У заявки одно активное предложение, прежние помечаются как заменённые.
 */
@Service
public class OfferService {

    private final OfferRepository offers;
    private final PricingCalculator pricing;
    private final StatusTransitionService transitions;
    private final Json json;

    public OfferService(OfferRepository offers, PricingCalculator pricing, StatusTransitionService transitions, Json json) {
        this.offers = offers;
        this.pricing = pricing;
        this.transitions = transitions;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Offer create(LoanApplication app, double amount, int termMonths, double spreadPct, Grade grade,
                        OfferExplanation explanation, String createdBy) {
        for (Offer old : offers.findByApplicationIdAndStatus(app.getId(), Offer.ACTIVE)) {
            old.setStatus(Offer.SUPERSEDED);
        }
        PricingCalculator.Quote q = pricing.quote(amount, termMonths, spreadPct, grade);
        boolean sameAsRequested = app.getRequestedAmount().compareTo(q.amount()) == 0
                && app.getRequestedTermMonths() == termMonths;

        Offer o = new Offer();
        o.setApplicationId(app.getId());
        o.setKind(sameAsRequested ? Offer.INITIAL : Offer.COUNTER);
        o.setAmount(q.amount());
        o.setTermMonths(q.termMonths());
        o.setRatePct(q.ratePct());
        o.setMonthlyPayment(q.monthlyPayment());
        o.setTotalPayment(q.totalPayment());
        o.setOverpayment(q.overpayment());
        o.setStatus(Offer.ACTIVE);
        o.setExplanation(json.write(explanation));
        o.setCreatedBy(createdBy);
        offers.save(o);
        app.setCurrentOfferId(o.getId());
        return o;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void accept(LoanApplication app, Long clientUserId) {
        StatusTransitionService.requireStatus(app, ApplicationStatus.OFFER_MADE);
        Offer offer = activeOffer(app);
        offer.setStatus(Offer.ACCEPTED);
        transitions.transition(app, ApplicationStatus.SIGNING, ActorType.CLIENT, clientUserId,
                "Клиент принял предложение");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void decline(LoanApplication app, Long clientUserId) {
        StatusTransitionService.requireStatus(app, ApplicationStatus.OFFER_MADE);
        Offer offer = activeOffer(app);
        offer.setStatus(Offer.DECLINED);
        transitions.transition(app, ApplicationStatus.CLOSED, ActorType.CLIENT, clientUserId,
                "Клиент отклонил предложение");
    }

    public Offer activeOffer(LoanApplication app) {
        List<Offer> active = offers.findByApplicationIdAndStatus(app.getId(), Offer.ACTIVE);
        if (active.isEmpty()) {
            throw ApiException.conflict("NO_ACTIVE_OFFER", "У заявки нет действующего предложения");
        }
        return active.get(0);
    }

    public OfferExplanation explanation(Offer offer) {
        return json.read(offer.getExplanation(), OfferExplanation.class);
    }
}
