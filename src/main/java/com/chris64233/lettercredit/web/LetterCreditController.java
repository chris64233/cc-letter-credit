package com.chris64233.lettercredit.web;

import com.chris64233.lettercredit.service.LetterCreditService;
import com.chris64233.lettercredit.web.dto.CreateLetterCreditRequest;
import com.chris64233.lettercredit.web.dto.LetterCreditResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/letter-credits")
public class LetterCreditController {

    private final LetterCreditService letterCreditService;

    public LetterCreditController(LetterCreditService letterCreditService) {
        this.letterCreditService = letterCreditService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LetterCreditResponse create(@RequestBody CreateLetterCreditRequest request) {
        return LetterCreditResponse.from(letterCreditService.createLetterCredit(
                request.lcNumber(), request.beneficiary(), request.currency(),
                request.maxAmount(), request.expiryDate(), request.allowedDocumentTypes()));
    }

    /**
     * 信用证余额查询。
     */
    @GetMapping("/{id}")
    public LetterCreditResponse get(@PathVariable long id) {
        return LetterCreditResponse.from(letterCreditService.getLetterCredit(id));
    }
}
