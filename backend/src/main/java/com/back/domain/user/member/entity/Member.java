package com.back.domain.user.member.entity;

import com.back.domain.reference.entity.Reference;
import com.back.domain.reference.entity.ReferenceReview;
import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.member.type.MemberRole;
import com.back.domain.user.member.type.MemberStatus;
import com.back.global.entity.SoftDeletable;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "members")
@Getter @Setter @Builder
@NoArgsConstructor
@AllArgsConstructor
public class Member extends SoftDeletable {

    @Column(
            name = "display_name",
            unique = true,
            nullable = false,
            length = 30
    )
    private String displayName;

    @Column(name = "member_role", nullable = false)
    @Enumerated(EnumType.STRING)
    private MemberRole role;

    @Column(name = "member_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private MemberStatus memberStatus;

    @Column
    private String profileImageUrl;

    @Column(name = "essential_terms_agreed", nullable = false)
    @Builder.Default
    private boolean essentialTermsAgreed = false;

    @OneToMany(
            mappedBy = "member"
//            cascade = CascadeType.ALL
//            orphanRemoval = true
    )
    @Builder.Default
    private List<AuthAccount> authAccounts =  new ArrayList<>();

    @OneToMany(mappedBy = "member")
    @Builder.Default
    private List<Reference> references = new ArrayList<>();

    @OneToMany(mappedBy = "reviewer")
    @Builder.Default
    private List<ReferenceReview> referenceReview = new ArrayList<>();

//    public void suspend() {
//        this.memberStatus = MemberStatus.BANNED;
//    }

    public boolean isSuspended() {
        return getMemberStatus().equals(MemberStatus.BANNED);
    }

    public void completeSignUp() {
        this.essentialTermsAgreed = true;
        this.role = MemberRole.GENERAL;
    }

    public boolean actuallyInActive() {
        return !memberStatus.equals(MemberStatus.ACTIVE) ||
                isDeleted() || !essentialTermsAgreed;
    }

    public void withdraw() {
        if (isDeleted())
            throw new ApiException(ErrorCode.CONFLICT, "This member is already withdrawn.");

        markAsDeleted();
    }
}
