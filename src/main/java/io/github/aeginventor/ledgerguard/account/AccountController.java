package io.github.aeginventor.ledgerguard.account;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "accounts", description = "계정과목표")
public class AccountController {

    private final AccountRepository accountRepository;

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping
    @Operation(summary = "계정과목 전체 목록", description = "코드 순으로 정렬한 계정과목표. 사용 중지 계정(active=false)도 포함한다.")
    public List<AccountResponse> list() {
        return accountRepository.findAllByOrderByCodeAsc().stream().map(AccountResponse::from).toList();
    }
}
