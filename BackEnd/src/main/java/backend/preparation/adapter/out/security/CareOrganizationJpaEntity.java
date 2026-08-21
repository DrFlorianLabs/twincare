package backend.preparation.adapter.out.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "care_organization")
class CareOrganizationJpaEntity {

    @Id
    @Column(length = 128)
    private String id;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    protected CareOrganizationJpaEntity() {
    }
}
