package io.hyperfoil.tools.h5m.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;

import java.util.ArrayList;
import java.util.List;

@Entity(name = "h5m_user")
public class User extends PanacheEntity {

    @Column(unique = true)
    public String username;

    public String role; // "admin" or "user"

    @ManyToMany(mappedBy = "members")
    public List<Team> teams = new ArrayList<>();

    public User() {}

    public User(String username, String role) {
        this.username = username;
        this.role = role;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof User that)) {
            return false;
        }
        return username.equals(that.username);
    }

    @Override
    public int hashCode() {
        return username.hashCode();
    }

    @Override
    public String toString() {
        return "User<" + id + ">[ username=" + username + " role=" + role + " ]";
    }
}
