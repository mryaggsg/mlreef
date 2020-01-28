package com.mlreef.rest.feature.auth

import com.mlreef.rest.*
import com.mlreef.rest.config.censor
import com.mlreef.rest.exceptions.Error
import com.mlreef.rest.exceptions.GitlabAlreadyExistingConflictException
import com.mlreef.rest.exceptions.GitlabBadRequestException
import com.mlreef.rest.exceptions.GitlabConnectException
import com.mlreef.rest.exceptions.UserAlreadyExistsException
import com.mlreef.rest.external_api.gitlab.*
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import java.util.UUID.randomUUID
import javax.transaction.Transactional


interface AuthService {
    fun createTokenDetails(token: String, account: Account, gitlabUser: GitlabUser): TokenDetails
    fun findAccountByToken(token: String): Account
    fun loginUser(plainPassword: String, username: String? = null, email: String? = null): Account
    fun registerUser(plainPassword: String, username: String, email: String): Account
    fun getBestToken(findAccount: Account?): AccountToken?
    fun findGitlabUserViaToken(token: String): GitlabUser
}


@Service("authService")
class GitlabAuthService(
    private val gitlabRestClient: GitlabRestClient,
    private val accountRepository: AccountRepository,
    private val personRepository: PersonRepository,
    private val accountTokenRepository: AccountTokenRepository,
    private val passwordEncoder: PasswordEncoder
) : AuthService {

    val log = LoggerFactory.getLogger(this::class.java)

    override fun loginUser(plainPassword: String, username: String?, email: String?): Account {
        val byUsername: Account? = if (username != null) accountRepository.findOneByUsername(username) else null
        val byEmail: Account? = if (email != null) accountRepository.findOneByEmail(email) else null

        val found: List<Account> = listOfNotNull(byUsername, byEmail).filter { account ->
            passwordEncoder.matches(plainPassword, account.passwordEncrypted)
        }

        val account = found.getOrNull(0)
            ?: throw BadCredentialsException("user not found")

        val accountToken = getBestToken(account)
            ?: throw BadCredentialsException("user token not found")

        // assert that user is found in gitlab
        findGitlabUserViaToken(accountToken.token)

        val accountUpdate = account.copy(lastLogin = I18N.dateTime())
        return accountRepository.save(accountUpdate)
    }

    override fun getBestToken(findAccount: Account?): AccountToken? {
        val findAllByUserId = accountTokenRepository.findAllByAccountId(findAccount!!.id)
        val sortedBy = findAllByUserId.filter { it.active && !it.revoked }.sortedBy { it.expiresAt }
        return sortedBy.getOrNull(0)
    }

    @Transactional
    override fun registerUser(plainPassword: String, username: String, email: String): Account {
        val encryptedPassword = passwordEncoder.encode(plainPassword)
        val byUsername: Account? = accountRepository.findOneByUsername(username)
        val byEmail: Account? = accountRepository.findOneByEmail(email)

        if (listOfNotNull(byUsername, byEmail).isNotEmpty()) {
            throw UserAlreadyExistsException(username, email)
        }

        val newGitlabGroup = createGitlabGroup(username)
        val newGitlabUser = createGitlabUser(username = username, email = email, password = plainPassword)
        val newGitlabToken = createGitlabToken(username, newGitlabUser)

        addGitlabUserToGroup(newGitlabUser, newGitlabGroup)

        val token = newGitlabToken.token

        val person = Person(id = randomUUID(), slug = username, name = username)
        val newUser = Account(id = randomUUID(), username = username, email = email, passwordEncrypted = encryptedPassword, person = person, gitlabId = newGitlabUser.id)
        val newToken = AccountToken(id = randomUUID(), accountId = newUser.id, token = token, gitlabId = newGitlabToken.id)

        personRepository.save(person)
        accountRepository.save(newUser)
        accountTokenRepository.save(newToken)

        return newUser
    }

    override fun findGitlabUserViaToken(token: String): GitlabUser {
        return try {
            gitlabRestClient.getUser(token)
        } catch (e: ResourceAccessException) {
            throw GitlabConnectException(e.message ?: "Cannot execute gitlabRestClient.getUser")
        } catch (e: Exception) {
            log.error(e.message, e)
            throw GitlabAlreadyExistingConflictException(Error.GitlabUserNotExisting, "Cannot find Gitlab user with this token ${token.censor()}")
        }
    }

    private fun createGitlabUser(username: String, email: String, password: String): GitlabUser {
        return try {
            val gitlabName = "mlreef-user-$username"
            gitlabRestClient.adminCreateUser(email = email, name = gitlabName, username = username, password = password)
        } catch (clientErrorException: HttpClientErrorException) {
            log.error(clientErrorException.message, clientErrorException)
            if (clientErrorException.rawStatusCode == 409) {
                // TODO FIXME: In production, this is not okay!
                log.info("Already existing dev user")
                val adminGetUsers = gitlabRestClient.adminGetUsers()
                adminGetUsers.first { it.username == username }
                // TODO USE THIS: throw GitlabAlreadyExistingConflictException(Error.GitlabUserCreationFailed, "Cannot create user for $username")
            } else {
                throw GitlabBadRequestException(Error.GitlabUserCreationFailed, "Cannot create user for $username")
            }
        }
    }

    private fun createGitlabToken(username: String, gitlabUser: GitlabUser): GitlabUserToken {
        return try {
            val gitlabUserId = gitlabUser.id
            val tokenName = "mlreef-user-token"
            gitlabRestClient.adminCreateUserToken(gitlabUserId = gitlabUserId, tokenName = tokenName)
        } catch (e: Exception) {
            log.error(e.message, e)
            throw GitlabAlreadyExistingConflictException(Error.GitlabUserTokenCreationFailed, "Cannot create user token for $username")
        }
    }

    private fun addGitlabUserToGroup(user: GitlabUser, group: GitlabGroup): GitlabUserInGroup {
        return try {
            val userId = user.id
            val groupId = group.id
            gitlabRestClient.adminAddUserToGroup(groupId = groupId, userId = userId)
        } catch (e: Exception) {
            log.error(e.message, e)
            throw GitlabAlreadyExistingConflictException(Error.GitlabUserAddingToGroupFailed, "Cannot add user ${user.name} to group ${group.name}")
        }
    }

    private fun createGitlabGroup(groupName: String, path: String? = null): GitlabGroup {
        return try {
            val gitlabName = "mlreef-group-$groupName"
            val gitlabPath = path ?: "$groupName-path"
            gitlabRestClient.adminCreateGroup(groupName = gitlabName, path = gitlabPath)
        } catch (e: Exception) {
            log.error(e.message, e)
            throw GitlabAlreadyExistingConflictException(Error.GitlabGroupCreationFailed, "Cannot create group $groupName")
        }
    }

    override fun createTokenDetails(token: String, account: Account, gitlabUser: GitlabUser): TokenDetails {
        return TokenDetails(
            token = token,
            accountId = account.id,
            personId = account.person.id,
            gitlabUser = gitlabUser,
            valid = (true)
        )
    }

    override fun findAccountByToken(token: String): Account {
        val findOneByToken = accountTokenRepository.findOneByToken(token)
            ?: throw BadCredentialsException("Token not found in Database")

        return accountRepository.findById2(findOneByToken.accountId)
            ?: throw BadCredentialsException("Token not attached to a Account in Database")
    }
}
