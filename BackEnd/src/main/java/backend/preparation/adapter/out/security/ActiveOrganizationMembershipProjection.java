package backend.preparation.adapter.out.security;

interface ActiveOrganizationMembershipProjection {

    String getOrganizationId();

    String getDisplayName();

    String getMembershipRole();
}
