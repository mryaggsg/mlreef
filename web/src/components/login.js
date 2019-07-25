import React from 'react';
import '../css/login.css';
import icon from '../images/ml_reef_icon_01.svg';
import {Redirect} from 'react-router-dom';

export default class Login extends React.Component {
    constructor(props){
        super(props);

        this.state = {
            email: "",
            password: "",
            redirect: false
        };

        this.submit = this.submit.bind(this);
        this.validateForm = this.validateForm.bind(this);
        this.cleanCredentialFields = this.cleanCredentialFields.bind(this);
    }    

    handleChange = event => {
        this.setState({
                [event.target.id]: event.target.value
            }
        )
    }

    validateForm = () => this.state.email.length > 0 
            && this.state.password.length > 0 
            && this.state.email === "camillo" 
            && this.state.password === "password";
        
    cleanCredentialFields(e){
        e.preventDefault();
        this.setState({
            email: "",
            password: "",
            redirect: false
        });
    }

    renderRedirect(){
        if(this.state.redirect){
            return <Redirect to="/home"/>
        }
    }

    submit(e){
        //test purposes
        if(this.state.email === "andres" && this.state.password === "andres"){
            const errorDiv = document.getElementById("errorDiv");
            errorDiv.classList.remove("invisible");
            return;
        }

        if(!this.validateForm()){
            const errorDiv = document.getElementById("errorDiv");
            errorDiv.classList.remove("invisible");
            return;
        }
        
        sessionStorage.auth = true;
        this.setState({
            redirect: true
        });
    }

    render(){
        return (
            <div id="login-container">
                {this.renderRedirect()}
                    <div id="icon-div">
                        <img src={icon} alt=""/>
                    </div>
                    <div id="errorDiv" className="invisible error border-div">
                        <p>    
                            Incorrect username or password
                        </p>
                        <div>
                            <button onClick={this.cleanCredentialFields} className="paragraph">Reset</button>
                        </div>
                    </div>
                <div className="login-form border-div">
                    <div className="title">    
                        Sign in to <b>MLreef</b>
                    </div>


                    <div className="form-container">
                        <div className="input-container paragraph">
                            <p>Username or email address</p>
                            <input id="email" type="text" value={this.state.email} onChange={this.handleChange}/>
                        </div>
                        <div className="input-container paragraph">
                            <p>Password</p>
                            <input id="password" type="password" value={this.state.password} onChange={this.handleChange}/>
                        </div>

                        <div id="sign-in-btn" className="input-container">
                            <button onClick={this.submit}>
                                <b>Sign in</b>
                            </button>
                        </div>
                    </div>

                </div>
                <div id="create-account-div" className="border-div paragraph">
                    <p>New to MLreef? <b>Create an account.</b></p>
                </div>
            </div>
        )
    }
}

